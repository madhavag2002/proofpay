package dev.proofpay.proofpay.webhook;

import dev.proofpay.proofpay.config.AppProperties;
import dev.proofpay.proofpay.paypal.PayPalClient;
import dev.proofpay.proofpay.paypal.PayPalClient.WebhookHeaders;
import dev.proofpay.proofpay.paypal.PayPalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives PayPal webhooks. This URL is public, so every event is verified with PayPal
 * before it can change an order.
 */
@RestController
class PayPalWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PayPalWebhookController.class);

    private final PayPalClient paypal;
    private final WebhookProcessor processor;
    private final AppProperties props;

    PayPalWebhookController(PayPalClient paypal, WebhookProcessor processor, AppProperties props) {
        this.paypal = paypal;
        this.processor = processor;
        this.props = props;
    }

    @PostMapping("/webhooks/paypal")
    ResponseEntity<String> receive(
            @RequestBody String rawBody,
            @RequestHeader(value = "PAYPAL-AUTH-ALGO", required = false) String authAlgo,
            @RequestHeader(value = "PAYPAL-CERT-URL", required = false) String certUrl,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-ID", required = false) String transmissionId,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-SIG", required = false) String transmissionSig,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-TIME", required = false) String transmissionTime) {

        WebhookHeaders headers = new WebhookHeaders(authAlgo, certUrl, transmissionId, transmissionSig, transmissionTime);
        if (!isAuthentic(headers, rawBody)) {
            // PayPal retries rejected deliveries, so a temporary verification problem is not lost.
            return ResponseEntity.status(401).body("not verified");
        }

        WebhookProcessor.Outcome outcome = processor.process(rawBody);
        if (outcome == WebhookProcessor.Outcome.INVALID) {
            return ResponseEntity.badRequest().body("invalid event");
        }
        return ResponseEntity.ok(outcome.name().toLowerCase());
    }

    private boolean isAuthentic(WebhookHeaders headers, String rawBody) {
        if (props.paypal().allowUnverifiedWebhooks()) {
            log.warn("PAYPAL_ALLOW_UNVERIFIED_WEBHOOKS=true: accepting a webhook without verification (local testing only)");
            return true;
        }
        if (props.paypal().isMock() || !headers.complete()) {
            log.warn("Rejected a webhook without PayPal headers");
            return false;
        }
        try {
            boolean ok = paypal.verifyWebhook(headers, rawBody, props.paypal().webhookId());
            if (!ok) {
                log.warn("PayPal said a webhook was not genuine (transmission {})", headers.transmissionId());
            }
            return ok;
        } catch (PayPalException e) {
            log.warn("Could not verify a webhook: {}", e.getMessage());
            return false;
        }
    }
}
