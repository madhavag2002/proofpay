package dev.proofpay.proofpay.dispute;

import dev.proofpay.proofpay.order.SellerOrder;
import dev.proofpay.proofpay.seller.SellerProfile;
import dev.proofpay.proofpay.seller.ShippingWindow;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The records ProofPay holds about the disputed order, each with an id the AI must cite.
 * Built by code, so the AI can only argue from what is really there.
 */
public record EvidencePack(List<Item> items, boolean hasTracking, boolean overdue) {

    public record Item(String id, String label, String text) {
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    public Set<String> ids() {
        return items.stream().map(Item::id).collect(Collectors.toSet());
    }

    public String forPrompt() {
        return items.stream().map(i -> "[" + i.id() + "] " + i.label() + ": " + i.text())
                .collect(Collectors.joining("\n"));
    }

    public static EvidencePack build(SellerOrder o, SellerProfile profile, CourierStatus courier, Clock clock) {
        if (o == null) {
            return new EvidencePack(List.of(new Item("E1", "No order",
                    "ProofPay has no order linked to this dispute.")), false, false);
        }
        List<Item> items = new ArrayList<>();
        if (o.getAgreedTerms() != null) {
            add(items, "Agreed terms on the PayPal invoice the buyer paid", o.getAgreedTerms());
        }
        add(items, "Payment", "Invoice " + o.getPaypalInvoiceId() + " paid on " + day(o.getPaidAt())
                + " (PayPal transaction " + o.getPaymentId() + ").");

        boolean hasTracking = o.getTrackingNumber() != null;
        add(items, "Tracking", hasTracking
                ? "Tracking " + o.getCarrier() + " " + o.getTrackingNumber() + " was added to PayPal. "
                + "Courier status, as reported by the seller: " + courier.name().toLowerCase().replace('_', ' ') + "."
                : "No tracking number on file.");

        boolean overdue = false;
        if (o.getPaidAt() != null) {
            long days = Duration.between(o.getPaidAt(), clock.instant()).toDays();
            String where = o.getShipToCountry() == null ? "this destination" : o.getShipToCountry();
            Optional<ShippingWindow> window = profile.windowFor(o.getShipToCountry());
            String text;
            if (window.isEmpty()) {
                text = "Paid " + days + " days ago. No usual delivery time is set for " + where + ".";
            } else {
                ShippingWindow w = window.get();
                LocalDate latest = o.getPaidAt().atZone(ZoneOffset.UTC).toLocalDate().plusDays(w.getMaxDays());
                String usual = "The seller's usual delivery time to " + where + " is "
                        + w.getMinDays() + "-" + w.getMaxDays() + " days";
                if (days > w.getMaxDays()) {
                    overdue = true;
                    text = "Paid " + days + " days ago. " + usual + ", so the parcel is overdue by "
                            + (days - w.getMaxDays()) + " days.";
                } else {
                    text = "Paid " + days + " days ago. " + usual
                            + ", so it is still within normal delivery time (expected by " + DATE.format(latest) + ").";
                }
            }
            add(items, "Delivery time", text);
        }
        if (!o.getAcceptedNotes().isEmpty()) {
            add(items, "What the buyer accepted in the chat", String.join("; ", o.getAcceptedNotes()));
        }
        String chat = o.getBuyerMessage();
        add(items, "Chat with the buyer (excerpt)", chat.length() > 1500 ? chat.substring(0, 1500) + "..." : chat);
        return new EvidencePack(List.copyOf(items), hasTracking, overdue);
    }

    private static void add(List<Item> items, String label, String text) {
        items.add(new Item("E" + (items.size() + 1), label, text));
    }

    private static String day(java.time.Instant instant) {
        return instant == null ? "an unknown date" : DATE.format(instant.atZone(ZoneOffset.UTC));
    }
}
