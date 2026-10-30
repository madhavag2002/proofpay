package dev.proofpay.proofpay.agent;

import dev.proofpay.proofpay.ai.AiException;
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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** OpenAI Chat Completions with tools. Not strict: MCP schemas use features strict mode rejects. */
public class OpenAiAgentModel implements AgentModel {

    private final RestClient http;
    private final AppProperties props;
    private final JsonMapper mapper;

    public OpenAiAgentModel(AppProperties props, JsonMapper mapper) {
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
                    throw new AiException(describeError(response.getStatusCode().value(), body));
                })
                .build();
    }

    @Override
    public JsonNode next(ArrayNode messages, ArrayNode tools) {
        ObjectNode req = mapper.createObjectNode();
        req.put("model", props.ai().model());
        req.put("max_completion_tokens", 4000);
        req.set("messages", messages);
        req.set("tools", tools);
        req.put("tool_choice", "auto");
        // One tool call per turn keeps the confirm flow simple.
        req.put("parallel_tool_calls", false);

        JsonNode response;
        try {
            response = http.post().uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(req)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (ResourceAccessException e) {
            throw new AiException("Could not reach OpenAI. Check your internet connection.", e);
        }
        if (response == null) {
            throw new AiException("OpenAI returned an empty response");
        }
        JsonNode message = response.path("choices").path(0).path("message");
        if (message.isMissingNode()) {
            throw new AiException("OpenAI returned no message");
        }
        return message;
    }

    private String describeError(int status, String body) {
        String detail = "";
        try {
            detail = mapper.readTree(body).path("error").path("message").asString("");
        } catch (JacksonException ignored) {
            // keep detail empty
        }
        return switch (status) {
            case 401 -> "OpenAI rejected the API key (401). Check OPENAI_API_KEY.";
            case 404 -> "OpenAI could not find the model (404). Check AI_MODEL.";
            case 429 -> "OpenAI rate limit or no credit left (429).";
            case 400 -> "OpenAI rejected the request (400): " + detail;
            default -> "OpenAI returned " + status + ". Try again in a moment.";
        };
    }
}
