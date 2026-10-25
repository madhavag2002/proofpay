package dev.proofpay.proofpay.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class StartupSummary {

    private static final Logger log = LoggerFactory.getLogger(StartupSummary.class);

    private final AppProperties props;

    StartupSummary(AppProperties props) {
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    void logSummary() {
        log.info("ProofPay ready for seller '{}'", props.sellerName());
        log.info("PayPal: mode={}, credentials present={}, webhook id present={}",
                props.paypal().mode(), props.paypal().hasCredentials(),
                AppProperties.notBlank(props.paypal().webhookId()));
        log.info("AI: effective mode={}, model={}",
                props.ai().isMock() ? "MOCK" : "LIVE", props.ai().model());
        log.info("Seller password enabled={}", props.passwordEnabled());
        if (props.paypal().allowUnverifiedWebhooks()) {
            log.warn("Unverified webhooks are ALLOWED. Local testing only, never on a public URL.");
        }
    }
}
