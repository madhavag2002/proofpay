package dev.proofpay.proofpay.paypal;

import dev.proofpay.proofpay.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.function.Supplier;

/** Calls the PayPal REST API: OAuth token, Invoicing v2. */
@Component
public class PayPalClient {

    private static final Logger log = LoggerFactory.getLogger(PayPalClient.class);

    private final RestClient http;
    private final AppProperties.PayPal config;
    private final JsonMapper mapper;

    private String cachedToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public PayPalClient(AppProperties props, JsonMapper mapper) {
        this.config = props.paypal();
        this.mapper = mapper;
        this.http = RestClient.builder()
                .baseUrl(config.baseUrl())
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    throw new PayPalException("PayPal returned " + response.getStatusCode().value()
                            + " for " + request.getMethod() + " " + request.getURI().getPath()
                            + ": " + summarizeError(body));
                })
                .build();
    }

    /** OAuth token, cached until one minute before it expires. The MCP server will reuse it later. */
    public synchronized String accessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }
        if (!config.hasCredentials()) {
            throw new PayPalException("Set PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET in .env");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        JsonNode token = call(() -> http.post()
                .uri("/v1/oauth2/token")
                .headers(h -> h.setBasicAuth(config.clientId(), config.clientSecret()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class));

        cachedToken = token.path("access_token").asString(null);
        if (cachedToken == null) {
            throw new PayPalException("PayPal token response had no access_token");
        }
        long expiresIn = token.path("expires_in").asLong(300);
        tokenExpiresAt = Instant.now().plusSeconds(Math.max(expiresIn - 60, 0));
        log.info("Got PayPal access token, valid for {} s", expiresIn);
        return cachedToken;
    }

    /** Creates a draft invoice and returns its id, e.g. INV2-XXXX-XXXX-XXXX-XXXX. */
    public String createDraftInvoice(ObjectNode payload, String requestId) {
        JsonNode created = call(() -> http.post()
                .uri("/v2/invoicing/invoices")
                .headers(h -> {
                    h.setBearerAuth(accessToken());
                    h.set("Prefer", "return=representation");
                    // Same id on a retry = PayPal returns the same invoice instead of a duplicate.
                    h.set("PayPal-Request-Id", requestId);
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(JsonNode.class));

        String id = created.path("id").asString(null);
        if (id == null) {
            // Without "return=representation" PayPal returns a link to the invoice instead.
            String href = created.path("href").asString("");
            id = href.isEmpty() ? null : href.substring(href.lastIndexOf('/') + 1);
        }
        if (id == null || id.isBlank()) {
            throw new PayPalException("PayPal did not return an invoice id");
        }
        return id;
    }

    /** Sends the invoice to the buyer. */
    public void sendInvoice(String invoiceId) {
        ObjectNode body = mapper.createObjectNode()
                .put("send_to_recipient", true)
                .put("send_to_invoicer", false);
        call(() -> http.post()
                .uri("/v2/invoicing/invoices/{id}/send", invoiceId)
                .headers(h -> h.setBearerAuth(accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity());
    }

    public JsonNode getInvoice(String invoiceId) {
        return call(() -> http.get()
                .uri("/v2/invoicing/invoices/{id}", invoiceId)
                .headers(h -> h.setBearerAuth(accessToken()))
                .retrieve()
                .body(JsonNode.class));
    }

    /** The page where the buyer views and pays the invoice. Null if PayPal doesn't include it. */
    public String recipientViewUrl(String invoiceId) {
        return getInvoice(invoiceId).path("detail").path("metadata").path("recipient_view_url").asString(null);
    }

    private <T> T call(Supplier<T> request) {
        try {
            T result = request.get();
            if (result == null) {
                throw new PayPalException("PayPal returned an empty response");
            }
            return result;
        } catch (ResourceAccessException e) {
            throw new PayPalException("Could not reach PayPal. Check your internet and PAYPAL_BASE_URL.", e);
        }
    }

    /** Pulls PayPal's error name, message and field issues out of an error body. */
    private String summarizeError(String body) {
        try {
            JsonNode error = mapper.readTree(body);
            StringBuilder s = new StringBuilder();
            s.append(error.path("name").asString(error.path("error").asString(""))).append(' ');
            s.append(error.path("message").asString(error.path("error_description").asString("")));
            for (JsonNode d : error.path("details")) {
                s.append(" [").append(d.path("field").asString("")).append(' ')
                        .append(d.path("issue").asString("")).append(']');
            }
            return s.toString().strip();
        } catch (RuntimeException e) {
            return "no details";
        }
    }
}
