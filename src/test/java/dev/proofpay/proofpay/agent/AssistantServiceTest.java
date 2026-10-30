package dev.proofpay.proofpay.agent;

import dev.proofpay.proofpay.ai.ChatInput;
import dev.proofpay.proofpay.order.DraftEdit;
import dev.proofpay.proofpay.order.OrderItem;
import dev.proofpay.proofpay.order.OrderService;
import dev.proofpay.proofpay.order.SellerOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "proofpay.ai.mode=mock",
        "proofpay.paypal.mode=mock",
        "spring.datasource.url=jdbc:h2:mem:assistanttest;DB_CLOSE_DELAY=-1"})
@Import(AssistantServiceTest.Fakes.class)
class AssistantServiceTest {

    static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** Replies in a fixed order, like a script. */
    static class FakeModel implements AgentModel {
        final Deque<JsonNode> replies = new ArrayDeque<>();
        final List<ArrayNode> seen = new ArrayList<>();

        @Override
        public JsonNode next(ArrayNode messages, ArrayNode tools) {
            seen.add(messages.deepCopy());
            return replies.isEmpty() ? text("(no more replies)") : replies.removeFirst();
        }
    }

    /** Records every call that would reach PayPal. */
    static class FakeMcp implements McpTools {
        final List<String> calls = new ArrayList<>();

        @Override public boolean isAvailable() { return true; }

        @Override
        public List<McpTool> listTools() {
            List<McpTool> tools = new ArrayList<>();
            for (String name : List.of("list_invoices", "list_disputes", "get_dispute", "create_shipment_tracking",
                    "send_invoice_reminder", "accept_dispute_claim", "create_refund", "cancel_sent_invoice")) {
                tools.add(new McpTool(name, "desc", MAPPER.readTree("{\"type\":\"object\",\"$schema\":\"x\"}")));
            }
            return tools;
        }

        @Override
        public McpResult callTool(String name, Map<String, Object> arguments) {
            calls.add(name);
            return new McpResult("{\"ok\":true,\"links\":[{\"href\":\"x\"}]}", false);
        }
    }

    @TestConfiguration
    static class Fakes {
        @Bean @Primary FakeModel fakeModel() { return new FakeModel(); }
        @Bean @Primary FakeMcp fakeMcp() { return new FakeMcp(); }
    }

    @Autowired AssistantService assistant;
    @Autowired FakeModel model;
    @Autowired FakeMcp mcp;
    @Autowired OrderService orders;

    @BeforeEach
    void reset() {
        model.replies.clear();
        model.seen.clear();
        mcp.calls.clear();
    }

    static JsonNode text(String content) {
        ObjectNode m = MAPPER.createObjectNode().put("role", "assistant");
        m.put("content", content);
        return m;
    }

    static JsonNode toolCall(String id, String name, String argsJson) {
        ObjectNode m = MAPPER.createObjectNode().put("role", "assistant");
        m.putNull("content");
        ObjectNode call = m.putArray("tool_calls").addObject().put("id", id).put("type", "function");
        call.putObject("function").put("name", name).put("arguments", argsJson);
        return m;
    }

    @Test
    void onlyAllowedToolsAreOffered() {
        assertThat(assistant.offeredToolNames()).containsExactlyInAnyOrder(
                "proofpay_orders", "list_invoices", "list_disputes", "get_dispute",
                "create_shipment_tracking", "send_invoice_reminder");
    }

    @Test
    void readToolsRunAtOnce() {
        model.replies.add(toolCall("c1", "list_disputes", "{}"));
        model.replies.add(text("You have no open disputes."));

        AgentConversation c = new AgentConversation();
        assistant.send(c, "Any disputes?");

        assertThat(mcp.calls).containsExactly("list_disputes");
        assertThat(c.getPending()).isNull();
        assertThat(c.getLines()).extracting(ChatLine::text).contains("Checked PayPal: disputes", "You have no open disputes.");
        // Links were removed before the result went to the AI.
        assertThat(model.seen.get(1).toString()).doesNotContain("href");
    }

    @Test
    void writeWaitsForConfirmThenSavesTracking() {
        SellerOrder paid = paidOrder("PAY-TRACK-1");
        model.replies.add(toolCall("c1", "create_shipment_tracking",
                "{\"transaction_id\":\"PAY-TRACK-1\",\"tracking_number\":\"D123\",\"carrier\":\"OTHER\"}"));

        AgentConversation c = new AgentConversation();
        assistant.send(c, "Add tracking D123 to Emily's order");

        assertThat(mcp.calls).isEmpty();                       // nothing reached PayPal yet
        assertThat(c.getPending()).isNotNull();
        assertThat(c.getPending().summary()).contains("D123").contains("PAY-TRACK-1");

        model.replies.add(text("Tracking added."));
        assistant.confirm(c);

        assertThat(mcp.calls).containsExactly("create_shipment_tracking");
        assertThat(c.getPending()).isNull();
        assertThat(orders.load(paid.getId()).getTrackingNumber()).isEqualTo("D123");
    }

    @Test
    void cancelledWriteNeverRuns() {
        model.replies.add(toolCall("c1", "send_invoice_reminder", "{\"invoice_id\":\"INV2-1\"}"));
        AgentConversation c = new AgentConversation();
        assistant.send(c, "Remind the buyer");

        model.replies.add(text("Okay, I did not send it."));
        assistant.cancel(c);

        assertThat(mcp.calls).isEmpty();
        assertThat(model.seen.get(1).toString()).contains("declined");
    }

    @Test
    void newMessageWhilePendingDeclinesTheWrite() {
        model.replies.add(toolCall("c1", "send_invoice_reminder", "{\"invoice_id\":\"INV2-1\"}"));
        AgentConversation c = new AgentConversation();
        assistant.send(c, "Remind the buyer");

        model.replies.add(text("Sure, what else?"));
        assistant.send(c, "Actually, never mind");

        assertThat(mcp.calls).isEmpty();
        assertThat(c.getPending()).isNull();
    }

    @Test
    void blockedToolsNeverRunEvenIfTheModelAsks() {
        model.replies.add(toolCall("c1", "accept_dispute_claim", "{\"dispute_id\":\"PP-1\"}"));
        model.replies.add(text("I can't accept disputes."));

        AgentConversation c = new AgentConversation();
        assistant.send(c, "Accept the dispute");

        assertThat(mcp.calls).isEmpty();
        assertThat(c.getPending()).isNull();
        assertThat(model.seen.get(1).toString()).contains("not available in ProofPay");
    }

    private SellerOrder paidOrder(String paymentId) {
        SellerOrder draft = orders.createFromChat("Emily Carter", "emily@example.com", "USD", ChatInput.pasted("4 coasters"));
        orders.approveAndSend(draft.getId(), new DraftEdit(
                List.of(new OrderItem("Coaster", "10 cm", 4, new BigDecimal("18"))), "Ohio", null, List.of()), true);
        orders.markPaidByInvoice(orders.load(draft.getId()).getPaypalInvoiceId(), paymentId);
        return orders.load(draft.getId());
    }
}
