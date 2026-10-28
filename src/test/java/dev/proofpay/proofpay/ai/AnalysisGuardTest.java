package dev.proofpay.proofpay.ai;

import dev.proofpay.proofpay.order.RiskLevel;
import dev.proofpay.proofpay.order.RiskType;
import dev.proofpay.proofpay.seller.SellerProfile;
import dev.proofpay.proofpay.seller.ShippingWindow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisGuardTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);
    private final SellerProfile seller = new SellerProfile(ShippingWindow.parse("US=12-20,GB=10-18,*=15-25"));

    private OrderAnalysis analysis(BigDecimal price, String country, String deadline) {
        return new OrderAnalysis(null, "en", "s",
                List.of(new OrderAnalysis.Item("Coaster", "10 cm", 4, price)),
                "usd", "somewhere", country, null, deadline, List.of(), List.of(), List.of(), "");
    }

    private OrderAnalysis clean(OrderAnalysis a, String chat) {
        return AnalysisGuard.clean(a, chat, seller, TODAY);
    }

    // ---- prices

    @Test
    void keepsPricesThatAreInTheChat() {
        OrderAnalysis cleaned = clean(analysis(new BigDecimal("18"), null, null), "they are $18 each");
        assertThat(cleaned.items().get(0).unitPrice()).isEqualByComparingTo("18");
        assertThat(cleaned.currency()).isEqualTo("USD");
        assertThat(cleaned.riskFlags()).isEmpty();
    }

    @Test
    void removesAnInventedPriceAndFlagsIt() {
        OrderAnalysis cleaned = clean(analysis(new BigDecimal("25"), null, null), "I want 4 coasters please");
        assertThat(cleaned.items().get(0).unitPrice()).isNull();
        assertThat(cleaned.openQuestions()).anyMatch(q -> q.contains("not in the chat"));
        assertThat(cleaned.riskFlags()).anyMatch(f -> f.type() == RiskType.MISSING_PRICE);
    }

    @Test
    void priceMatchingHandlesFormatsButNotOtherNumbers() {
        assertThat(AnalysisGuard.priceAppearsIn(new BigDecimal("65.5"), "tray is $65.50")).isTrue();
        assertThat(AnalysisGuard.priceAppearsIn(new BigDecimal("1200"), "total 1,200 rupees")).isTrue();
        assertThat(AnalysisGuard.priceAppearsIn(new BigDecimal("18"), "18.00 each")).isTrue();
        assertThat(AnalysisGuard.priceAppearsIn(new BigDecimal("18"), "size 180 cm")).isFalse();
        assertThat(AnalysisGuard.priceAppearsIn(new BigDecimal("18"), "since 2018")).isFalse();
    }

    @Test
    void usesTheTranscriptForScreenshots() {
        OrderAnalysis fromImages = new OrderAnalysis("Ravi: coasters are $18", "en", "s",
                List.of(new OrderAnalysis.Item("Coaster", "", 1, new BigDecimal("18"))),
                null, null, null, null, null, List.of(), List.of(), List.of(), null);
        assertThat(clean(fromImages, null).items().get(0).unitPrice()).isEqualByComparingTo("18");
    }

    // ---- delivery, judged against this seller's settings

    @Test
    void deadlineBeforeFastestDeliveryIsHighRisk() {
        // US takes 12-20 days; a deadline 7 days away cannot be met.
        OrderAnalysis cleaned = clean(analysis(new BigDecimal("18"), "US", "2026-11-03"), "$18");
        assertThat(cleaned.riskFlags()).singleElement().satisfies(f -> {
            assertThat(f.type()).isEqualTo(RiskType.UNREALISTIC_DELIVERY);
            assertThat(f.level()).isEqualTo(RiskLevel.HIGH);
            assertThat(f.prevents()).isEqualTo("MERCHANDISE_OR_SERVICE_NOT_RECEIVED");
        });
    }

    @Test
    void deadlineInsideTheWindowIsMediumRisk() {
        // 15 days away: after the 12-day best case, before the 20-day worst case.
        OrderAnalysis cleaned = clean(analysis(new BigDecimal("18"), "US", "2026-11-11"), "$18");
        assertThat(cleaned.riskFlags()).singleElement().extracting(OrderAnalysis.Flag::level).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void comfortableDeadlineHasNoFlag() {
        OrderAnalysis cleaned = clean(analysis(new BigDecimal("18"), "US", "2026-12-15"), "$18");
        assertThat(cleaned.riskFlags()).isEmpty();
    }

    @Test
    void sameDeadlineCanBeFineForOneCountryAndRiskyForAnother() {
        // 19 days away: within the GB window (10-18) but not the US worst case (20).
        assertThat(clean(analysis(new BigDecimal("18"), "GB", "2026-11-15"), "$18").riskFlags()).isEmpty();
        assertThat(clean(analysis(new BigDecimal("18"), "US", "2026-11-15"), "$18").riskFlags()).hasSize(1);
    }

    @Test
    void unknownCountryUsesTheDefaultWindow() {
        // "*" is 15-25 days; 10 days away is too soon.
        OrderAnalysis cleaned = clean(analysis(new BigDecimal("18"), "AU", "2026-11-06"), "$18");
        assertThat(cleaned.riskFlags()).singleElement().extracting(OrderAnalysis.Flag::level).isEqualTo(RiskLevel.HIGH);
    }

    // aiDeliveryOpinionsAreIgnored() and anything after it were cut off in the pasted message.
}
