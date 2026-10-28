package dev.proofpay.proofpay.paypal;

import dev.proofpay.proofpay.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Picks the real or mock invoice gateway from PAYPAL_MODE. */
@Configuration
class PaymentsConfig {

    private static final Logger log = LoggerFactory.getLogger(PaymentsConfig.class);

    @Bean
    InvoiceGateway invoiceGateway(AppProperties props, PayPalClient client, InvoicePayloadBuilder builder) {
        if (props.paypal().isMock()) {
            log.warn("PAYPAL_MODE=mock: invoices are faked and nothing is sent to PayPal");
            return new MockInvoiceGateway();
        }
        log.info("PayPal live mode against {}", props.paypal().baseUrl());
        return new PayPalInvoiceGateway(client, builder, props.sellerName());
    }
}
