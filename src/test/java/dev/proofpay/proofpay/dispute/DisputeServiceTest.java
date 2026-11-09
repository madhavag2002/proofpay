package dev.proofpay.proofpay.dispute;

import dev.proofpay.proofpay.ai.ChatInput;
import dev.proofpay.proofpay.order.DraftEdit;
import dev.proofpay.proofpay.order.OrderItem;
import dev.proofpay.proofpay.order.OrderService;
import dev.proofpay.proofpay.order.OrderStatus;
import dev.proofpay.proofpay.order.SellerOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "proofpay.ai.mode=mock",
        "proofpay.paypal.mode=mock",
        "spring.datasource.url=jdbc:h2:mem:disputetest;DB_CLOSE_DELAY=-1"})
class DisputeServiceTest {

    @Autowired DisputeService disputes;
    @Autowired OrderService orders;
    @Autowired JsonMapper mapper;

    private SellerOrder paidOrder(String paymentId) {
        SellerOrder d = orders.createFromChat("Emily Carter", "emily@example.com", "USD", ChatInput.pasted("4 coasters"));
        orders.approveAndSend(d.getId(), new DraftEdit(
                List.of(new OrderItem("Coaster", "10 cm, blue", 4, new BigDecimal("18"))), "Ohio", null, List.of()), true);
        orders.markPaidByInvoice(orders.load(d.getId()).getPaypalInvoiceId(), paymentId);
        return orders.load(d.getId());
    }

    /** Shaped like the real sandbox dispute PP-R-XYA-10197416. */
    private String disputeJson(String id, String invoiceId, String status) {
        return """
                {"dispute_id":"%s","reason":"MERCHANDISE_OR_SERVICE_NOT_RECEIVED","status":"%s",
                 "dispute_life_cycle_stage":"CHARGEBACK","seller_response_due_date":null,
                 "dispute_amount":{"currency_code":"USD","value":"72.00"},
                 "disputed_transactions":[{"seller_transaction_id":"PAY-OTHER","invoice_number":"%s",
                   "seller_protection_eligible":true}],
                 "allowed_response_options":{"accept_claim":{"accept_claim_types":["REFUND"]}},
                 "messages":null}
                """.formatted(id, status, invoiceId);
    }

    @Test
    void disputeIsMatchedByInvoiceAndOrderBecomesDisputed() {
        SellerOrder order = paidOrder("PAY-D1");
        DisputeCase c = disputes.upsertFromPayPal(mapper.readTree(disputeJson("PP-T-1", order.getPaypalInvoiceId(), "UNDER_REVIEW")));

        assertThat(c.getOrderId()).isEqualTo(order.getId());
        assertThat(c.isSellerProtectionEligible()).isTrue();
        assertThat(c.getAllowedResponses()).isEqualTo("accept_claim");
        assertThat(orders.load(order.getId()).getStatus()).isEqualTo(OrderStatus.DISPUTED);

        // The same dispute again (webhook retry or status change) updates the same case.
        DisputeCase again = disputes.upsertFromPayPal(mapper.readTree(
                disputeJson("PP-T-1", order.getPaypalInvoiceId(), "WAITING_FOR_SELLER_RESPONSE")));
        assertThat(again.getId()).isEqualTo(c.getId());
        assertThat(again.getStatus()).isEqualTo("WAITING_FOR_SELLER_RESPONSE");
    }

    @Test
    void analysisIsCheckedAndCanOnlyBeSentWhenPayPalWaits() {
        SellerOrder order = paidOrder("PAY-D2");
        DisputeCase c = disputes.upsertFromPayPal(mapper.readTree(disputeJson("PP-T-2", order.getPaypalInvoiceId(), "UNDER_REVIEW")));

        DisputeAnalysis a = disputes.analyze(c.getId());
        // No tracking on this order: the guard caps the mock's MEDIUM at WEAK.
        assertThat(a.strength()).isEqualTo(CaseStrength.WEAK);
        assertThat(disputes.load(c.getId()).getDraftReply()).isNotBlank();
        assertThat(disputes.load(c.getId()).canSubmit()).isFalse(); // PayPal is reviewing

        assertThatThrownBy(() -> disputes.submit(c.getId())).isInstanceOf(IllegalStateException.class);
        assertThat(disputes.load(c.getId()).getSubmittedAt()).isNull();
    }

    @Test
    void unmatchedDisputeIsStillRecorded() {
        DisputeCase c = disputes.upsertFromPayPal(mapper.readTree(disputeJson("PP-T-3", "INV2-NOT-OURS", "OPEN")));
        assertThat(c.getOrderId()).isNull();
    }
}
