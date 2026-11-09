package dev.proofpay.proofpay.dispute;

import dev.proofpay.proofpay.dispute.DisputeAnalysis.ClaimPoint;
import dev.proofpay.proofpay.dispute.DisputeAnalysis.Support;
import dev.proofpay.proofpay.order.OrderItem;
import dev.proofpay.proofpay.order.SellerOrder;
import dev.proofpay.proofpay.seller.SellerProfile;
import dev.proofpay.proofpay.seller.ShippingWindow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DisputeGuardTest {

    private final EvidencePack noTracking = new EvidencePack(
            List.of(new EvidencePack.Item("E1", "Terms", "4 coasters")), false, false);
    private final EvidencePack withTracking = new EvidencePack(
            List.of(new EvidencePack.Item("E1", "Terms", "4 coasters"),
                    new EvidencePack.Item("E2", "Tracking", "DTDC D1")), true, false);

    private DisputeAnalysis ai(CaseStrength s, Recommendation r, String ref) {
        return new DisputeAnalysis(List.of(new ClaimPoint("never arrived", "tracking", ref, Support.YES)),
                s, r, List.of("AI reason"), List.of(), " draft ");
    }

    @Test
    void notReceivedWithoutTrackingIsNeverAFight() {
        DisputeAnalysis out = DisputeGuard.apply(ai(CaseStrength.STRONG, Recommendation.FIGHT, "E1"),
                noTracking, Playbook.NOT_RECEIVED, CourierStatus.UNKNOWN);
        assertThat(out.strength()).isEqualTo(CaseStrength.WEAK);
        assertThat(out.recommendation()).isEqualTo(Recommendation.REPLY_WITH_UPDATE);
        assertThat(out.reasons()).anyMatch(r -> r.startsWith("ProofPay rule"));
        assertThat(out.draftReply()).isEqualTo("draft");
    }

    @Test
    void deliveredTrackingIsAtLeastMedium() {
        DisputeAnalysis out = DisputeGuard.apply(ai(CaseStrength.WEAK, Recommendation.FIGHT, "E2"),
                withTracking, Playbook.NOT_RECEIVED, CourierStatus.DELIVERED);
        assertThat(out.strength()).isEqualTo(CaseStrength.MEDIUM);
        assertThat(out.recommendation()).isEqualTo(Recommendation.FIGHT);
    }

    @Test
    void citationsMustExist() {
        DisputeAnalysis out = DisputeGuard.apply(ai(CaseStrength.STRONG, Recommendation.FIGHT, "E9"),
                withTracking, Playbook.NOT_AS_DESCRIBED, CourierStatus.UNKNOWN);
        assertThat(out.claimPoints().get(0).evidenceRef()).isEqualTo("NONE");
        assertThat(out.claimPoints().get(0).supportsSeller()).isEqualTo(Support.PARTLY);
    }

    @Test
    void playbookPicksEvidenceTypeByTracking() {
        assertThat(Playbook.forReason("MERCHANDISE_OR_SERVICE_NOT_RECEIVED")).isEqualTo(Playbook.NOT_RECEIVED);
        assertThat(Playbook.NOT_RECEIVED.evidenceTypeFor(true)).isEqualTo("PROOF_OF_FULFILLMENT");
        assertThat(Playbook.NOT_RECEIVED.evidenceTypeFor(false)).isEqualTo("OTHER");
        assertThat(Playbook.forReason("SOMETHING_NEW")).isEqualTo(Playbook.OTHER);
    }

    @Test
    void evidenceSaysWhetherTheParcelIsOverdue() {
        SellerOrder o = new SellerOrder("Emily", "e@example.com", "chat", "USD");
        o.recordAiDraft("en", "s", List.of(new OrderItem("Coaster", "10 cm", 4, new BigDecimal("18"))),
                "Ohio", null, List.of(), List.of());
        o.recordDestination("US");
        o.markInvoiceSent("terms", "INV2-1", null);
        o.markPaid("PAY-1");
        SellerProfile profile = new SellerProfile(ShippingWindow.parse("US=12-20"));

        EvidencePack early = EvidencePack.build(o, profile, CourierStatus.UNKNOWN,
                Clock.offset(Clock.systemUTC(), Duration.ofDays(5)));
        assertThat(early.overdue()).isFalse();
        assertThat(early.forPrompt()).contains("still within normal delivery time");

        EvidencePack late = EvidencePack.build(o, profile, CourierStatus.UNKNOWN,
                Clock.offset(Clock.systemUTC(), Duration.ofDays(25)));
        assertThat(late.overdue()).isTrue();
        assertThat(late.forPrompt()).contains("overdue by 5 days");
    }
}
