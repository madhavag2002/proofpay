package dev.proofpay.proofpay.ai;

import dev.proofpay.proofpay.config.AppProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Calls OpenAI with one forced, strict function and returns the function's arguments as JSON. */
public class OpenAiFunctionCaller {

    private final RestClient http;
    private final AppProperties props;
    private final JsonMapper mapper;

    public OpenAiFunctionCaller(AppProperties props, JsonMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(120));
        this.http = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(props.ai().baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.ai().apiKey())
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    throw new AiException(describe(response.getStatusCode().value(), body));
                })
                .build();
    }

    public JsonNode call(String systemPrompt, String userText, String function, String description, ObjectNode schema) {
        ObjectNode req = mapper.createObjectNode();
        req.put("model", props.ai().model());
        req.put("max_completion_tokens", 8000);
        var messages = req.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userText);
        ObjectNode fn = req.putArray("tools").addObject().put("type", "function").putObject("function");
        fn.put("name", function);
        fn.put("description", description);
        fn.put("strict", true);
        fn.set("parameters", schema.deepCopy());
        req.putObject("tool_choice").put("type", "function").putObject("function").put("name", function);

        JsonNode response;
        try {
            response = http.post().uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON).body(req).retrieve().body(JsonNode.class);
        } catch (ResourceAccessException e) {
            throw new AiException("Could not reach OpenAI. Check your internet connection.", e);
        }
        if (response == null) {
            throw new AiException("OpenAI returned an empty response");
        }
        JsonNode message = response.path("choices").path(0).path("message");
        if (message.hasNonNull("refusal")) {
            throw new AiException("The AI declined this request.");
        }
        for (JsonNode call : message.path("tool_calls")) {
            if (function.equals(call.path("function").path("name").asString(""))) {
                try {
                    return mapper.readTree(call.path("function").path("arguments").asString("{}"));
                } catch (JacksonException e) {
                    throw new AiException("The AI's answer was not valid JSON. Try again.", e);
                }
            }
        }
        throw new AiException("The AI did not return an answer. Try again.");
    }

    private String describe(int status, String body) {
        String detail = "";
        try {
            detail = mapper.readTree(body).path("error").path("message").asString("");
        } catch (JacksonException ignored) {
            // keep detail empty
        }
        return switch (status) {
            case 401 -> "OpenAI rejected the API key (401).";
            case 404 -> "OpenAI could not find the model (404). Check AI_MODEL.";
            case 429 -> "OpenAI rate limit or no credit left (429).";
            case 400 -> "OpenAI rejected the request (400): " + detail;
            default -> "OpenAI returned " + status + ". Try again in a moment.";
        };
    }
}
