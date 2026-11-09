package dev.proofpay.proofpay.agent;

import dev.proofpay.proofpay.config.AppProperties;
import dev.proofpay.proofpay.order.OrderService;
import dev.proofpay.proofpay.order.OrderStatus;
import dev.proofpay.proofpay.order.SellerOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The seller assistant. The AI chooses tools; reads run at once, writes wait for the seller's Confirm.
 * Only tools in AgentTools are ever offered, so dangerous PayPal tools cannot be called at all.
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    static final int MAX_ROUNDS = 8;
    static final int MAX_TOOL_CHARS = 6000;

    private final AgentModel model;
    private final McpTools mcp;
    private final OrderService orders;
    private final JsonMapper mapper;
    private final AppProperties props;
    private final Clock clock;
    private final String promptTemplate;

    private volatile ArrayNode toolDefinitions;

    public AssistantService(AgentModel model, McpTools mcp, OrderService orders, JsonMapper mapper,
                            AppProperties props, Clock clock) {
        this.model = model;
        this.mcp = mcp;
        this.orders = orders;
        this.mapper = mapper;
        this.props = props;
        this.clock = clock;
        this.promptTemplate = load("ai/assistant-prompt.txt");
    }

    public boolean isAvailable() {
        return mcp.isAvailable() && !props.ai().isMock();
    }

    // ---- What the seller can do

    public void send(AgentConversation c, String text) {
        synchronized (c) {
            if (c.getPending() != null) {
                // OpenAI needs an answer for every tool call before the next message.
                decline(c, "The seller sent a new message instead of confirming. Do not run this unless asked again.");
            }
            c.addUser(text);
            runLoop(c);
        }
    }

    public void confirm(AgentConversation c) {
        synchronized (c) {
            PendingAction p = c.getPending();
            if (p == null) {
                return;
            }
            c.clearPending();
            log.info("Seller confirmed {} with {}", p.toolName(), p.argumentsText());
            McpResult result = runMcp(p.toolName(), p.arguments());
            c.addToolResult(p.callId(), (result.error() ? "Error: " : "") + JsonSlimmer.slim(mapper, result.text(), MAX_TOOL_CHARS));
            c.addLine(ChatLine.Kind.ACTION, (result.error() ? "Failed: " : "Done: ") + p.summary());
            if (!result.error() && AgentTools.CREATE_TRACKING.equals(p.toolName())) {
                saveTrackingOnOrder(c, p.arguments());
            }
            runLoop(c);
        }
    }

    public void cancel(AgentConversation c) {
        synchronized (c) {
            PendingAction p = c.getPending();
            if (p == null) {
                return;
            }
            decline(c, "The seller declined this action. Do not try it again unless they ask.");
            c.addLine(ChatLine.Kind.ACTION, "Cancelled: " + p.summary());
            runLoop(c);
        }
    }

    // ---- The loop

    private void runLoop(AgentConversation c) {
        for (int round = 0; round < MAX_ROUNDS; round++) {
            JsonNode message = model.next(c.toModelMessages(systemPrompt()), tools());
            String content = message.path("content").isString() ? message.path("content").asString("") : "";
            JsonNode calls = message.path("tool_calls");
            ArrayNode toolCalls = calls.isArray() ? (ArrayNode) calls.deepCopy() : null;

            c.addAssistant(content, toolCalls);
            if (!content.isBlank()) {
                c.addLine(ChatLine.Kind.ASSISTANT, content);
            }
            if (toolCalls == null || toolCalls.isEmpty()) {
                return; // the AI answered; wait for the seller
            }

            boolean paused = false;
            for (JsonNode call : toolCalls) {
                String id = call.path("id").asString("");
                String name = call.path("function").path("name").asString("");
                ObjectNode args = parseArgs(call.path("function").path("arguments").asString("{}"));

                if (paused) {
                    c.addToolResult(id, "Not run: waiting for the seller to confirm an earlier action.");
                    continue;
                }
                if (args == null) {
                    c.addToolResult(id, "Error: the arguments were not valid JSON.");
                    continue;
                }
                switch (AgentTools.kindOf(name)) {
                    case LOCAL -> {
                        c.addToolResult(id, proofpayOrders(args.path("filter").asString("all")));
                        c.addLine(ChatLine.Kind.ACTION, "Checked ProofPay orders");
                    }
                    case READ -> {
                        McpResult r = runMcp(name, args);
                        c.addToolResult(id, (r.error() ? "Error: " : "") + JsonSlimmer.slim(mapper, r.text(), MAX_TOOL_CHARS));
                        c.addLine(ChatLine.Kind.ACTION, "Checked PayPal: " + AgentTools.label(name));
                    }
                    case WRITE -> {
                        c.setPending(new PendingAction(id, name, args, describe(name, args)));
                        paused = true;
                    }
                    case BLOCKED -> c.addToolResult(id, "This tool is not available in ProofPay.");
                }
            }
            if (paused) {
                return; // the page shows Confirm / Cancel
            }
        }
        c.addLine(ChatLine.Kind.ASSISTANT, "I stopped after several steps. Try asking in a simpler way.");
    }

    // ---- Tools

    /** ProofPay's own tool plus the allowed PayPal tools. Built once. */
    ArrayNode tools() {
        ArrayNode cached = toolDefinitions;
        if (cached != null) {
            return cached;
        }
        ArrayNode defs = mapper.createArrayNode();
        defs.add(proofpayOrdersDefinition());
        for (McpTool tool : mcp.listTools()) {
            if (!AgentTools.isOffered(tool.name())) {
                continue;
            }
            ObjectNode fn = defs.addObject().put("type", "function").putObject("function");
            fn.put("name", tool.name());
            String description = tool.description() == null ? "" : tool.description();
            if (AgentTools.kindOf(tool.name()) == AgentTools.Kind.WRITE) {
                description = "WRITE ACTION: runs only after the seller clicks Confirm in ProofPay. " + description;
            }
            fn.put("description", description);
            JsonNode schema = tool.inputSchema() == null ? mapper.createObjectNode() : tool.inputSchema().deepCopy();
            if (schema instanceof ObjectNode obj) {
                obj.remove("$schema");
            }
            fn.set("parameters", schema);
        }
        log.info("Assistant tools: {}", defs.findValuesAsString("name"));
        toolDefinitions = defs;
        return defs;
    }

    private ObjectNode proofpayOrdersDefinition() {
        ObjectNode def = mapper.createObjectNode().put("type", "function");
        ObjectNode fn = def.putObject("function");
        fn.put("name", AgentTools.PROOFPAY_ORDERS);
        fn.put("description", "The seller's orders in ProofPay: buyer, status, total, PayPal invoice id, "
                + "payment_id (the PayPal transaction id, needed to add tracking), tracking, days since paid.");
        ObjectNode params = fn.putObject("parameters");
        params.put("type", "object");
        ObjectNode filter = params.putObject("properties").putObject("filter");
        filter.put("type", "string");
        filter.putArray("enum").add("all").add("paid_without_tracking").add("unpaid").add("disputed");
        params.putArray("required").add("filter");
        return def;
    }

    String proofpayOrders(String filter) {
        ArrayNode out = mapper.createArrayNode();
        for (SellerOrder o : orders.listAll()) {
            boolean keep = switch (filter) {
                case "paid_without_tracking" -> o.getStatus() == OrderStatus.PAID && o.getTrackingNumber() == null;
                case "unpaid" -> o.getStatus() == OrderStatus.INVOICE_SENT;
                case "disputed" -> o.getStatus() == OrderStatus.DISPUTED;
                default -> true;
            };
            if (!keep || out.size() >= 20) {
                continue;
            }
            ObjectNode n = out.addObject();
            n.put("order", "#" + o.getId());
            n.put("buyer", o.getBuyerName());
            n.put("status", o.getStatus().name());
            BigDecimal total = o.total();
            n.put("total", total == null ? "incomplete" : total.toPlainString() + " " + o.getCurrency());
            n.put("paypal_invoice_id", o.getPaypalInvoiceId());
            n.put("payment_id", o.getPaymentId());
            n.put("tracking", o.getTrackingNumber() == null ? null : o.getCarrier() + " " + o.getTrackingNumber());
            if (o.getPaidAt() != null) {
                n.put("days_since_paid", Duration.between(o.getPaidAt(), clock.instant()).toDays());
            }
        }
        return out.toString();
    }

    private McpResult runMcp(String name, ObjectNode args) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.convertValue(args, Map.class);
            return mcp.callTool(name, map);
        } catch (McpException e) {
            return new McpResult(e.getMessage(), true);
        }
    }

    private void saveTrackingOnOrder(AgentConversation c, ObjectNode args) {
        String number = JsonSlimmer.findText(args, "tracking_number");
        String carrier = JsonSlimmer.findText(args, "carrier");
        String otherName = JsonSlimmer.findText(args, "carrier_name_other");
        if (otherName != null && (carrier == null || "OTHER".equalsIgnoreCase(carrier))) {
            carrier = otherName; // keep "DTDC" rather than PayPal's generic "OTHER"
        }
        String transaction = Optional.ofNullable(JsonSlimmer.findText(args, "transaction_id"))
                .orElse(JsonSlimmer.findText(args, "capture_id"));
        if (number == null || transaction == null) {
            return;
        }
        orders.recordTrackingByPayment(transaction, carrier, number).ifPresent(orderId ->
                c.addLine(ChatLine.Kind.ACTION, "Saved tracking on order #" + orderId));
    }

    static String describe(String tool, ObjectNode args) {
        if (AgentTools.CREATE_TRACKING.equals(tool)) {
            return "Add tracking " + nz(JsonSlimmer.findText(args, "tracking_number"))
                    + " (" + nz(JsonSlimmer.findText(args, "carrier")) + ") to PayPal transaction "
                    + nz(JsonSlimmer.findText(args, "transaction_id"));
        }
        if (AgentTools.SEND_REMINDER.equals(tool)) {
            return "Send a payment reminder for invoice " + nz(JsonSlimmer.findText(args, "invoice_id"));
        }
        return tool + " " + args;
    }

    // ---- Helpers

    private void decline(AgentConversation c, String reason) {
        PendingAction p = c.getPending();
        if (p != null) {
            c.addToolResult(p.callId(), reason);
            c.clearPending();
        }
    }

    private ObjectNode parseArgs(String raw) {
        try {
            JsonNode node = mapper.readTree(raw == null || raw.isBlank() ? "{}" : raw);
            return node instanceof ObjectNode obj ? obj : null;
        } catch (JacksonException e) {
            return null;
        }
    }

    private String systemPrompt() {
        return promptTemplate.replace("{{TODAY}}", LocalDate.now(clock).toString());
    }

    private static String nz(String s) {
        return s == null ? "?" : s;
    }

    private static String load(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing resource " + path, e);
        }
    }

    // For tests
    List<String> offeredToolNames() {
        return tools().findValuesAsString("name");
    }
}
