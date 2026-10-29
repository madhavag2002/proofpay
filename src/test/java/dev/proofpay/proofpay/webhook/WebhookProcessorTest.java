package dev.proofpay.proofpay.webhook;

import dev.proofpay.proofpay.ai.ChatInput;
import dev.proofpay.proofpay.order.DraftEdit;
import dev.proofpay.proofpay.order.OrderItem;
import dev.proofpay.proofpay.order.OrderService;
import dev.proofpay.proofpay.order.OrderStatus;
import dev.proofpay.proofpay.order.SellerOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "proofpay.ai.mode=mock",
        "proofpay.paypal.mode=mock",
        "spring.datasource.url=jdbc:h2:mem:webhooktest;DB_CLOSE_DELAY=-1"})
class WebhookProcessorTest {

    @Autowired
    WebhookProcessor processor;

    @Autowired
    OrderService orders;

    private SellerOrder sentOrder() {
        SellerOrder draft = orders.createFromChat("Emily Carter", "emily@example.com", "USD", ChatInput.pasted("4 coasters"));
        orders.approveAndSend(draft.getId(), new DraftEdit(
                List.of(new OrderItem("Coaster", "10 cm, blue", 4, new BigDecimal("18"))),
                "Ohio", null, List.of()), true);
        return orders.load(draft.getId());
    }

    private static String paidEvent(String eventId, String invoiceId) {
        return """
                {"id":"%s","event_type":"INVOICING.INVOICE.PAID",
                 "resource":{"invoice":{"id":"%s","status":"PAID",
                   "payments":{"transactions":[{"payment_id":"PAY-777","amount":{"currency_code":"USD","value":"72.00"}}]}}}}
                """.formatted(eventId, invoiceId);
    }

    @Test
    void paidEventMarksTheOrderPaidOnce() {
        SellerOrder order = sentOrder();
        String event = paidEvent("WH-EVT-" + order.getId(), order.getPaypalInvoiceId());

        assertThat(processor.process(event)).isEqualTo(WebhookProcessor.Outcome.HANDLED);
        SellerOrder paid = orders.load(order.getId());
        assertThat(paid.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.getPaymentId()).isEqualTo("PAY-777");

        // PayPal retries: the same event id is ignored.
        assertThat(processor.process(event)).isEqualTo(WebhookProcessor.Outcome.DUPLICATE);
    }

    @Test
    void paidEventForAnUnknownInvoiceDoesNotFail() {
        assertThat(processor.process(paidEvent("WH-EVT-UNKNOWN", "INV2-NOT-OURS")))
                .isEqualTo(WebhookProcessor.Outcome.HANDLED);
    }

    @Test
    void otherEventTypesAreIgnored() {
        assertThat(processor.process("{\"id\":\"WH-OTHER-1\",\"event_type\":\"INVOICING.INVOICE.CREATED\"}"))
                .isEqualTo(WebhookProcessor.Outcome.IGNORED);
    }

    @Test
    void malformedEventsAreRejected() {
        assertThat(processor.process("not json")).isEqualTo(WebhookProcessor.Outcome.INVALID);
        assertThat(processor.process("{\"id\":\"x\"}")).isEqualTo(WebhookProcessor.Outcome.INVALID);
    }
}
