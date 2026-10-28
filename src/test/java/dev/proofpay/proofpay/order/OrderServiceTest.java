package dev.proofpay.proofpay.order;

import dev.proofpay.proofpay.ai.ChatInput;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "proofpay.ai.mode=mock",
        "proofpay.paypal.mode=mock",
        "spring.datasource.url=jdbc:h2:mem:ordertest;DB_CLOSE_DELAY=-1"})
class OrderServiceTest {

    @Autowired
    OrderService service;

    private SellerOrder draft() {
        // The mock analyzer returns one item with no quantity or price, a HIGH and a MEDIUM flag.
        return service.createFromChat("Emily Carter", "emily@example.com", "USD", ChatInput.pasted("4 coasters please"));
    }

    private DraftEdit complete() {
        return new DraftEdit(List.of(new OrderItem("Coaster", "10 cm, deep blue", 4, new BigDecimal("18"))),
                "Columbus, Ohio", "By Dec 15", List.of("Handmade variation is fine"));
    }

    @Test
    void draftKeepsGapsEmpty() {
        SellerOrder order = draft();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DRAFT);
        assertThat(order.getItems().get(0).getQuantity()).isNull();
        assertThat(order.getItems().get(0).getUnitPrice()).isNull();
        assertThat(order.getRiskFlags()).hasSize(2);
        assertThat(order.getClarificationMessage()).isNotBlank();
    }

    @Test
    void fullFlowFromDraftToPaid() {
        long id = draft().getId();

        // Gaps block sending; nothing reaches PayPal.
        SendResult gaps = service.approveAndSend(id, null, false);
        assertThat(gaps.outcome()).isEqualTo(SendResult.Outcome.MISSING_FIELDS);
        assertThat(gaps.check().missing()).hasSize(2);

        // Filled in, but a HIGH risk is open: the seller must confirm.
        assertThat(service.approveAndSend(id, complete(), false).outcome())
                .isEqualTo(SendResult.Outcome.NEEDS_CONFIRMATION);
        assertThat(service.load(id).getStatus()).isEqualTo(OrderStatus.DRAFT);

        // Risks handled: sent.
        service.resolveFlag(id, 0);
        assertThat(service.approveAndSend(id, complete(), false).isSent()).isTrue();

        SellerOrder sent = service.load(id);
        assertThat(sent.getStatus()).isEqualTo(OrderStatus.INVOICE_SENT);
        assertThat(sent.getPaypalInvoiceId()).startsWith("MOCK-INV-");
        assertThat(sent.isSentDespiteOpenRisk()).isFalse();
        assertThat(sent.getAgreedTerms()).contains("4 x Coaster - 10 cm, deep blue");

        // A second click does not send again.
        assertThat(service.approveAndSend(id, complete(), false).outcome()).isEqualTo(SendResult.Outcome.ALREADY_SENT);
        assertThat(service.load(id).getPaypalInvoiceId()).isEqualTo(sent.getPaypalInvoiceId());

        // Paid twice (a webhook retry) is harmless.
        service.markPaidByInvoice(sent.getPaypalInvoiceId(), "PAY-1");
        service.markPaidByInvoice(sent.getPaypalInvoiceId(), "PAY-1");
        assertThat(service.load(id).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(service.load(id).getPaymentId()).isEqualTo("PAY-1");
    }

    @Test
    void sendAnywayIsRecorded() {
        long id = draft().getId();
        assertThat(service.approveAndSend(id, complete(), true).isSent()).isTrue();
        assertThat(service.load(id).isSentDespiteOpenRisk()).isTrue();
    }

    @Test
    void incompleteDraftCanBeSavedForLater() {
        long id = draft().getId();
        DraftEdit partial = new DraftEdit(List.of(new OrderItem("Coaster", "blue", null, new BigDecimal("18"))),
                null, null, List.of());
        ApprovalCheck check = service.saveDraft(id, partial);
        assertThat(check.missing()).containsExactly("'Coaster' needs a quantity. It was not settled in the chat.");
        assertThat(service.load(id).getItems().get(0).getUnitPrice()).isEqualByComparingTo("18");
    }

    @Test
    void currencyMismatchBecomesAQuestion() {
        SellerOrder order = service.createFromChat("Lena", "lena@example.com", "EUR", ChatInput.pasted("2 vases"));
        assertThat(order.getCurrency()).isEqualTo("EUR");
        assertThat(order.getOpenQuestions()).anyMatch(q -> q.contains("mentions USD"));
    }

    @Test
    void cannotEditAfterSending() {
        long id = draft().getId();
        service.approveAndSend(id, complete(), true);
        assertThatThrownBy(() -> service.saveDraft(id, complete())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unknownOrderIsReported() {
        assertThatThrownBy(() -> service.load(999_999)).isInstanceOf(OrderNotFoundException.class);
    }
}
