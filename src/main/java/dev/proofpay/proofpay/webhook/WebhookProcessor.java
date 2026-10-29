package dev.proofpay.proofpay.webhook;

import dev.proofpay.proofpay.order.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Acts on a verified PayPal event, exactly once per event id. */
@Service
public class WebhookProcessor {

    private static final Logger log = LoggerFactory.getLogger(WebhookProcessor.class);

    static final String INVOICE_PAID = "INVOICING.INVOICE.PAID";

    public enum Outcome { HANDLED, IGNORED, DUPLICATE, INVALID }

    private final OrderService orders;
    private final ProcessedWebhookEventRepository processed;
    private final JsonMapper mapper;

    public WebhookProcessor(OrderService orders, ProcessedWebhookEventRepository processed, JsonMapper mapper) {
        this.orders = orders;
        this.processed = processed;
        this.mapper = mapper;
    }

    public Outcome process(String rawBody) {
        JsonNode event;
        try {
            event = mapper.readTree(rawBody);
        } catch (JacksonException e) {
            return Outcome.INVALID;
        }
        String eventId = event.path("id").asString("");
        String eventType = event.path("event_type").asString("");
        if (eventId.isBlank() || eventType.isBlank()) {
            return Outcome.INVALID;
        }
        if (processed.existsById(eventId)) {
            log.info("Webhook {} ({}) already handled; ignoring the retry", eventId, eventType);
            return Outcome.DUPLICATE;
        }

        Outcome outcome;
        if (INVOICE_PAID.equals(eventType)) {
            handleInvoicePaid(event);
            outcome = Outcome.HANDLED;
        } else {
            // CUSTOMER.DISPUTE.CREATED is added in Step 12.
            log.info("Ignoring webhook type {}", eventType);
            outcome = Outcome.IGNORED;
        }

        try {
            processed.saveAndFlush(new ProcessedWebhookEvent(eventId, eventType));
        } catch (DataIntegrityViolationException e) {
            // Two copies of the same event arrived together. Marking paid is safe to repeat, so this is harmless.
            return Outcome.DUPLICATE;
        }
        return outcome;
    }

    private void handleInvoicePaid(JsonNode event) {
        JsonNode resource = event.path("resource");
        // The paid invoice is under resource.invoice; fall back to resource itself if the shape differs.
        JsonNode invoice = resource.has("invoice") ? resource.path("invoice") : resource;
        String invoiceId = invoice.path("id").asString("");
        if (invoiceId.isBlank()) {
            log.warn("Paid event {} has no invoice id", event.path("id").asString(""));
            return;
        }
        String paymentId = null;
        for (JsonNode tx : invoice.path("payments").path("transactions")) {
            String id = tx.path("payment_id").asString("");
            if (!id.isBlank()) {
                paymentId = id; // keep the latest payment
            }
        }
        orders.markPaidByInvoice(invoiceId, paymentId);
    }
}
