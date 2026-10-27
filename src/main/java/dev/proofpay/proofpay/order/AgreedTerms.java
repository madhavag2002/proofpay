package dev.proofpay.proofpay.order;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The agreed terms written into the PayPal invoice: extracted from the chat by AI,
 * checked and confirmed by the seller, then paid by the buyer on PayPal.
 */
public final class AgreedTerms {

    /** PayPal's limit for the invoice terms_and_conditions field. */
    public static final int MAX_LENGTH = 4000;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private AgreedTerms() {
    }

    public static String render(SellerOrder order, LocalDate agreedOn) {
        StringBuilder t = new StringBuilder();
        t.append("Terms agreed with ").append(order.getBuyerName())
         .append(" via chat on ").append(DATE.format(agreedOn)).append(":\n");

        int n = 1;
        for (OrderItem item : order.getItems()) {
            t.append(n++).append(". ").append(item.getQuantity()).append(" x ").append(item.getName());
            if (item.getSpecs() != null && !item.getSpecs().isBlank()) {
                t.append(" - ").append(item.getSpecs().strip());
            }
            t.append('\n');
        }

        line(t, "Ship to", order.getShipTo());
        line(t, "Delivery", order.getDeliveryBy());

        if (!order.getAcceptedNotes().isEmpty()) {
            t.append("Buyer accepted in chat: ").append(String.join("; ", order.getAcceptedNotes())).append('\n');
        }

        t.append("Paying this invoice confirms acceptance of these terms.");
        return truncate(t.toString(), MAX_LENGTH);
    }

    private static void line(StringBuilder t, String label, String value) {
        if (value != null && !value.isBlank()) {
            t.append(label).append(": ").append(value.strip()).append('\n');
        }
    }

    public static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
