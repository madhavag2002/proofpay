package dev.proofpay.proofpay.ai;

import dev.proofpay.proofpay.config.AppProperties;
import dev.proofpay.proofpay.seller.SellerProfile;
import dev.proofpay.proofpay.seller.SellerProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Calls the OpenAI Chat Completions API with one forced, strict function, so the answer
 * always matches order-analysis-schema.json instead of being free text.
 */
public class OpenAiOrderAnalyzer implements OrderAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(OpenAiOrderAnalyzer.class);

    static final String FUNCTION = "record_order_analysis";

    private final RestClient http;
    private final AppProperties props;
    private final JsonMapper mapper;
    private final SellerProfileService profiles;
    private final ObjectNode schema;
    private final String promptTemplate;

    public OpenAiOrderAnalyzer(AppProperties props, JsonMapper mapper, SellerProfileService profiles) {
        this.props = props;
        this.mapper = mapper;
        this.profiles = profiles;
        this.schema = (ObjectNode) mapper.readTree(load("ai/order-analysis-schema.json"));
        this.promptTemplate = load("ai/order-analysis-prompt.txt");

        // Reading screenshots can take a while, so allow up to 2 minutes for an answer.
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(120));

        this.http = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(props.ai().baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.ai().apiKey())
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    throw new AiException(describeError(response.getStatusCode().value(), body));
                })
                .build();
    }

    @Override
    public OrderAnalysis analyze(ChatInput input) {
        SellerProfile profile = profiles.current();
        LocalDate today = LocalDate.now(ZoneId.of(props.zoneOrDefault()));

        long start = System.currentTimeMillis();
        JsonNode response;
        try {
            response = http.post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildRequest(input, profile, today))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (ResourceAccessException e) {
            throw new AiException("Could not reach OpenAI. Check your internet connection.", e);
        }
        OrderAnalysis raw = parse(response);
        log.info("Analyzed {} chat in {} ms: {} items, {} risk flags", input.source(),
                System.currentTimeMillis() - start, raw.items().size(), raw.riskFlags().size());
        return AnalysisGuard.clean(raw, input.text(), profile, today);
    }

    ObjectNode buildRequest(ChatInput input, SellerProfile profile, LocalDate today) {
        ObjectNode req = mapper.createObjectNode();
        req.put("model", props.ai().model());
        // Reasoning models spend part of this on thinking, so leave room.
        req.put("max_completion_tokens", 8000);

        ArrayNode messages = req.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt(profile, today));

        ObjectNode user = messages.addObject();
        user.put("role", "user");
        ArrayNode content = user.putArray("content");
        content.addObject().put("type", "text").put("text", userText(input));
        for (ChatImage image : input.images()) {
            content.addObject().put("type", "image_url").putObject("image_url").put("url", image.dataUrl());
        }

        ObjectNode function = req.putArray("tools").addObject().put("type", "function").putObject("function");
        function.put("name", FUNCTION);
        function.put("description", "Record the draft order and the dispute risks found in the chat.");
        function.put("strict", true);
        function.set("parameters", schema.deepCopy());

        req.putObject("tool_choice").put("type", "function").putObject("function").put("name", FUNCTION);
        return req;
    }

    OrderAnalysis parse(JsonNode response) {
        if (response == null) {
            throw new AiException("OpenAI returned an empty response");
        }
        JsonNode choice = response.path("choices").path(0);
        JsonNode message = choice.path("message");
        if (message.hasNonNull("refusal")) {
            throw new AiException("The AI declined to read this chat. Fill in the order by hand.");
        }
        if ("length".equals(choice.path("finish_reason").asString(""))) {
            throw new AiException("The chat was too long for the AI. Paste only the messages about this order.");
        }
        for (JsonNode call : message.path("tool_calls")) {
            JsonNode fn = call.path("function");
            if (FUNCTION.equals(fn.path("name").asString(""))) {
                try {
                    return mapper.readValue(fn.path("arguments").asString(""), OrderAnalysis.class);
                } catch (JacksonException e) {
                    throw new AiException("The AI's answer did not match the expected format. Try again.", e);
                }
            }
        }
        throw new AiException("The AI did not return a draft. Try again or fill in the order by hand.");
    }

    private String systemPrompt(SellerProfile profile, LocalDate today) {
        return promptTemplate
                .replace("{{TODAY}}", today.toString())
                .replace("{{SHIPPING_WINDOWS}}", profile.describeShipping());
    }

    private static String userText(ChatInput input) {
        return switch (input.source()) {
            case SCREENSHOTS -> "Chat source: SCREENSHOTS. The chat is in the " + input.images().size()
                    + " attached image(s), in order. Fill in transcript.";
            case WHATSAPP_EXPORT -> "Chat source: WHATSAPP_EXPORT\n<chat>\n" + input.text() + "\n</chat>";
            case PASTE -> "Chat source: PASTE\n<chat>\n" + input.text() + "\n</chat>";
        };
    }

    /** Readable errors that never echo the key (OpenAI's 401 message can contain part of it). */
    private String describeError(int status, String body) {
        String detail = "";
        try {
            detail = mapper.readTree(body).path("error").path("message").asString("");
        } catch (JacksonException ignored) {
            // keep detail empty
        }
        return switch (status) {
            case 401 -> "OpenAI rejected the API key (401). Check OPENAI_API_KEY in .env.";
            case 404 -> "OpenAI could not find the model (404). Check AI_MODEL in .env.";
            case 429 -> "OpenAI rate limit or no credit left (429). Check billing at platform.openai.com.";
            case 400 -> "OpenAI rejected the request (400): " + detail;
            default -> "OpenAI returned " + status + ". Try again in a moment.";
        };
    }

    private static String load(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing resource " + path, e);
        }
    }

    @Override
    public boolean isMock() {
        return false;
    }
}
