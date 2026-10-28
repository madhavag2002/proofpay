package dev.proofpay.proofpay.order;

import java.util.ArrayList;
import java.util.List;

/**
 * What still stops an order from being invoiced. Used by the service (the real gate)
 * and by the review page (to show the seller what to fill in).
 *
 * @param missing       must be filled before the invoice can be sent
 * @param warnings      should be filled; the invoice protects the seller less without them
 * @param openHighRisk  a HIGH risk flag is not marked handled; the seller must choose "Send anyway"
 */
public record ApprovalCheck(List<String> missing, List<String> warnings, boolean openHighRisk) {

    public ApprovalCheck {
        missing = List.copyOf(missing);
        warnings = List.copyOf(warnings);
    }

    public boolean canSend() {
        return missing.isEmpty();
    }

    public static ApprovalCheck of(SellerOrder order) {
        List<String> missing = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<OrderItem> items = order.getItems();

        if (items.isEmpty()) {
            missing.add("Add at least one item.");
        }
        for (int i = 0; i < items.size(); i++) {
            OrderItem item = items.get(i);
            boolean named = item.getName() != null && !item.getName().isBlank();
            String label = named ? "'" + item.getName().strip() + "'" : "Item " + (i + 1);

            if (!named) {
                missing.add("Item " + (i + 1) + " needs a name.");
            }
            if (item.getQuantity() == null || item.getQuantity() < 1) {
                missing.add(label + " needs a quantity. It was not settled in the chat.");
            }
            if (item.getUnitPrice() == null || item.getUnitPrice().signum() <= 0) {
                missing.add(label + " needs a price. It was not agreed in the chat.");
            }
            if (item.getSpecs() == null) {
                warnings.add(label + " has no agreed details. Add size, colour or material so the invoice protects you.");
            }
        }
        if (order.getShipTo() == null || order.getShipTo().isBlank()) {
            warnings.add("No ship-to address. Add it so tracking can be matched to the buyer.");
        }
        return new ApprovalCheck(missing, warnings, order.hasOpenHighRisk());
    }
}
