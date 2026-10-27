package dev.proofpay.proofpay.paypal;

import dev.proofpay.proofpay.config.AppProperties;
import dev.proofpay.proofpay.order.AgreedTerms;
import dev.proofpay.proofpay.order.ChatSource;
import dev.proofpay.proofpay.order.OrderItem;
import dev.proofpay.proofpay.order.RiskFlag;
import dev.proofpay.proofpay.order.RiskLevel;
import dev.proofpay.proofpay.order.RiskType;
import dev.proofpay.proofpay.order.SellerOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Sends one test invoice built from an order. Runs only with the "spike" profile. */
@Component
@Profile("spike")
class PayPalSpike implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PayPalSpike.class);

    private final PayPalClient paypal;
    private final InvoicePayloadBuilder builder;
    private final AppProperties props;
    private final String buyerEmail;

    PayPalSpike(PayPalClient paypal, InvoicePayloadBuilder builder, AppProperties props,
                @Value("${SANDBOX_BUYER_EMAIL:}") String buyerEmail) {
        this.paypal = paypal;
        this.builder = builder;
        this.props = props;
        this.buyerEmail = buyerEmail;
    }

    @Override
    public void run(String... args) {
        if (buyerEmail.isBlank()) {
            log.error("Set SANDBOX_BUYER_EMAIL in .env to your US sandbox Personal account email");
            return;
        }

        // A hand-built order standing in for what Steps 6-8 will produce from a real chat.
        SellerOrder order = new SellerOrder("Emily Carter", buyerEmail, "spike test", "USD");

        // 1. What the AI extracted from the chat.
        order.recordAiDraft("en", "Spike test order",
                List.of(new OrderItem("Marble coaster", "Round, 10 cm, deep blue lapis inlay", 4, new BigDecimal("18")),
                        new OrderItem("Marble tray", "30 x 20 cm, matching inlay", 1, new BigDecimal("65"))),
                "Columbus, Ohio, USA", "Before 15 Dec",
                List.of("Slight pattern variation is fine because each piece is handmade"), List.of());

        // 2. The risk the AI flagged, with its suggested clarification.
        order.recordRiskCheck(ChatSource.PASTE,
                List.of(new RiskFlag(RiskType.VAGUE_SPEC, RiskLevel.HIGH, "Coaster size was not stated",
                        "MERCHANDISE_OR_SERVICE_NOT_AS_DESCRIBED")),
                "Hi Emily, just to confirm: 10 cm coasters in deep blue lapis?");

        // 3. The seller sent the clarification, the specs above include the answer, so the flag is handled.
        order.resolveFlag(0);

        String terms = AgreedTerms.render(order, LocalDate.now());
        log.info("Open high risks: {}. Agreed terms:\n{}", order.hasOpenHighRisk(), terms);

        ObjectNode payload = builder.build(order, terms, props.sellerName());
        String invoiceId = paypal.createDraftInvoice(payload, "spike-" + UUID.randomUUID());
        paypal.sendInvoice(invoiceId);
        log.info("Sent invoice {}. Buyer pay page: {}", invoiceId, paypal.recipientViewUrl(invoiceId));
    }
}
