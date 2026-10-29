package dev.proofpay.proofpay.paypal;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookVerificationBodyTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void splicesTheRawEventUnchanged() {
        // Trailing zeros and key order must reach PayPal exactly as received.
        String raw = "{\"id\":\"WH-1\",\"event_type\":\"INVOICING.INVOICE.PAID\",\"resource\":{\"amount\":10.50}}";
        PayPalClient.WebhookHeaders headers = new PayPalClient.WebhookHeaders(
                "SHA256withRSA", "https://api.sandbox.paypal.com/cert", "t-1", "sig", "2026-10-28T10:00:00Z");

        String body = PayPalClient.verificationBody(mapper, headers, "WH-ID-9", raw);

        assertThat(body).endsWith(",\"webhook_event\":" + raw + "}");
        JsonNode parsed = mapper.readTree(body);
        assertThat(parsed.path("webhook_id").asString()).isEqualTo("WH-ID-9");
        assertThat(parsed.path("transmission_id").asString()).isEqualTo("t-1");
        assertThat(parsed.path("webhook_event").path("event_type").asString()).isEqualTo("INVOICING.INVOICE.PAID");
    }

    @Test
    void headersMustAllBePresent() {
        assertThat(new PayPalClient.WebhookHeaders("a", "b", "c", "d", "e").complete()).isTrue();
        assertThat(new PayPalClient.WebhookHeaders("a", null, "c", "d", "e").complete()).isFalse();
    }
}
