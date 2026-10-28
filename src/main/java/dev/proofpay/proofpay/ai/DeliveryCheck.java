package dev.proofpay.proofpay.ai;

import dev.proofpay.proofpay.order.RiskLevel;
import dev.proofpay.proofpay.order.RiskType;
import dev.proofpay.proofpay.seller.SellerProfile;
import dev.proofpay.proofpay.seller.ShippingWindow;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * Compares the buyer's deadline with this seller's delivery time to that destination.
 * Plain code, not AI: the result depends only on the seller's own settings.
 */
final class DeliveryCheck {

    static final String NOT_RECEIVED = "MERCHANDISE_OR_SERVICE_NOT_RECEIVED";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private DeliveryCheck() {
    }

    static Optional<OrderAnalysis.Flag> check(LocalDate deadline, String country, SellerProfile profile, LocalDate today) {
        if (deadline == null) {
            return Optional.empty();
        }
        String where = country == null ? "this destination" : country;
        Optional<ShippingWindow> window = profile.windowFor(country);
        if (window.isEmpty()) {
            return Optional.of(new OrderAnalysis.Flag(RiskType.UNREALISTIC_DELIVERY, RiskLevel.MEDIUM,
                    "Buyer needs it by " + DATE.format(deadline) + ", but no delivery time is set for " + where
                            + ". Check the deadline yourself or add it in Settings.", NOT_RECEIVED));
        }
        ShippingWindow w = window.get();
        LocalDate earliest = today.plusDays(w.getMinDays());
        LocalDate latest = today.plusDays(w.getMaxDays());
        String usual = "delivery to " + where + " usually takes " + w.getMinDays() + "-" + w.getMaxDays() + " days";

        if (deadline.isBefore(earliest)) {
            return Optional.of(new OrderAnalysis.Flag(RiskType.UNREALISTIC_DELIVERY, RiskLevel.HIGH,
                    "Buyer needs it by " + DATE.format(deadline) + ", but " + usual
                            + ". Even if paid today it would arrive around " + DATE.format(earliest) + " at the earliest.",
                    NOT_RECEIVED));
        }
        if (deadline.isBefore(latest)) {
            return Optional.of(new OrderAnalysis.Flag(RiskType.UNREALISTIC_DELIVERY, RiskLevel.MEDIUM,
                    "Buyer needs it by " + DATE.format(deadline) + "; " + usual
                            + ", so it may arrive as late as " + DATE.format(latest) + ".", NOT_RECEIVED));
        }
        return Optional.empty();
    }
}
