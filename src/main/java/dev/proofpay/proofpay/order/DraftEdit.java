package dev.proofpay.proofpay.order;

import java.util.List;
import java.util.Objects;

/** The seller's edits from the review page. Gaps are allowed; ApprovalCheck decides if it can be sent. */
public record DraftEdit(List<OrderItem> items, String shipTo, String deliveryBy, List<String> acceptedNotes) {

    public DraftEdit {
        items = items == null ? List.of() : items.stream().filter(Objects::nonNull).toList();
        shipTo = blankToNull(shipTo, 255);
        deliveryBy = blankToNull(deliveryBy, 255);
        acceptedNotes = acceptedNotes == null ? List.of() : acceptedNotes.stream()
                .filter(Objects::nonNull).map(String::strip).filter(s -> !s.isEmpty())
                .map(s -> OrderItem.clip(s, 500)).toList();
    }

    private static String blankToNull(String s, int max) {
        return s == null || s.isBlank() ? null : OrderItem.clip(s.strip(), max);
    }
}
