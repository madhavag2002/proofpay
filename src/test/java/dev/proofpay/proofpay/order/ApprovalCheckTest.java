package dev.proofpay.proofpay.order;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalCheckTest {

    private SellerOrder draft(List<OrderItem> items, String shipTo) {
        SellerOrder o = new SellerOrder("Emily Carter", "emily@example.com", "chat", "USD");
        o.recordAiDraft("en", "s", items, shipTo, null, List.of(), List.of());
        return o;
    }

    @Test
    void completeOrderCanBeSent() {
        ApprovalCheck check = ApprovalCheck.of(draft(
                List.of(new OrderItem("Coaster", "10 cm, blue", 4, new BigDecimal("18"))), "Ohio, US"));
        assertThat(check.canSend()).isTrue();
        assertThat(check.warnings()).isEmpty();
    }

    @Test
    void unsettledQuantityAndPriceBlockSending() {
        // The vague chat: "maybe 4 or 5" coasters, and a tray with no price.
        ApprovalCheck check = ApprovalCheck.of(draft(List.of(
                new OrderItem("coasters", "blue-ish", null, new BigDecimal("18")),
                new OrderItem("tray", "", 1, null)), "Ohio"));

        assertThat(check.canSend()).isFalse();
        assertThat(check.missing()).containsExactly(
                "'coasters' needs a quantity. It was not settled in the chat.",
                "'tray' needs a price. It was not agreed in the chat.");
        assertThat(check.warnings()).anyMatch(w -> w.startsWith("'tray' has no agreed details"));
    }

    @Test
    void missingShipToIsOnlyAWarning() {
        ApprovalCheck check = ApprovalCheck.of(draft(
                List.of(new OrderItem("Coaster", "10 cm", 4, new BigDecimal("18"))), null));
        assertThat(check.canSend()).isTrue();
        assertThat(check.warnings()).anyMatch(w -> w.startsWith("No ship-to"));
    }

    @Test
    void noItemsBlocksSending() {
        assertThat(ApprovalCheck.of(draft(List.of(), "Ohio")).missing()).containsExactly("Add at least one item.");
    }

    @Test
    void openHighRiskIsReported() {
        SellerOrder o = draft(List.of(new OrderItem("Coaster", "10 cm", 4, new BigDecimal("18"))), "Ohio");
        o.recordRiskCheck(ChatSource.PASTE, List.of(new RiskFlag(RiskType.VAGUE_SPEC, RiskLevel.HIGH,
                "colour unclear", "MERCHANDISE_OR_SERVICE_NOT_AS_DESCRIBED")), "msg");
        assertThat(ApprovalCheck.of(o).openHighRisk()).isTrue();
        o.resolveFlag(0);
        assertThat(ApprovalCheck.of(o).openHighRisk()).isFalse();
    }
}
