package dev.proofpay.proofpay.web;

import dev.proofpay.proofpay.order.DraftEdit;
import dev.proofpay.proofpay.order.OrderItem;
import dev.proofpay.proofpay.order.SellerOrder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The review page's editable fields. Empty fields stay empty; completely blank rows are ignored. */
public class DraftForm {

    static final int SPARE_ROWS = 2;

    private List<Row> items = new ArrayList<>();
    private String shipTo;
    private String deliveryBy;
    /** One note per line. */
    private String acceptedNotes;
    private boolean sendAnyway;

    public static DraftForm from(SellerOrder order) {
        DraftForm form = new DraftForm();
        for (OrderItem item : order.getItems()) {
            form.items.add(new Row(item.getName(), item.getSpecs(), item.getQuantity(), item.getUnitPrice()));
        }
        form.shipTo = order.getShipTo();
        form.deliveryBy = order.getDeliveryBy();
        form.acceptedNotes = String.join("\n", order.getAcceptedNotes());
        form.padSpareRows();
        return form;
    }

    public DraftEdit toEdit() {
        List<OrderItem> result = new ArrayList<>();
        for (Row row : items) {
            if (row == null || row.isBlank()) {
                continue;
            }
            String name = row.getName() == null ? "" : row.getName().strip();
            result.add(new OrderItem(name, row.getSpecs(), row.getQuantity(), row.getUnitPrice()));
        }
        List<String> notes = acceptedNotes == null ? List.of() : Arrays.asList(acceptedNotes.split("\\R"));
        return new DraftEdit(result, shipTo, deliveryBy, notes);
    }

    /** Keeps two empty rows at the bottom, so the seller can add an item the AI missed. */
    public void padSpareRows() {
        long blank = items.stream().filter(r -> r == null || r.isBlank()).count();
        for (long i = blank; i < SPARE_ROWS; i++) {
            items.add(new Row());
        }
    }

    public List<Row> getItems() { return items; }
    public void setItems(List<Row> items) { this.items = items; }
    public String getShipTo() { return shipTo; }
    public void setShipTo(String shipTo) { this.shipTo = shipTo; }
    public String getDeliveryBy() { return deliveryBy; }
    public void setDeliveryBy(String deliveryBy) { this.deliveryBy = deliveryBy; }
    public String getAcceptedNotes() { return acceptedNotes; }
    public void setAcceptedNotes(String acceptedNotes) { this.acceptedNotes = acceptedNotes; }
    public boolean isSendAnyway() { return sendAnyway; }
    public void setSendAnyway(boolean sendAnyway) { this.sendAnyway = sendAnyway; }

    public static class Row {
        private String name;
        private String specs;
        private Integer quantity;
        private BigDecimal unitPrice;

        public Row() {
        }

        Row(String name, String specs, Integer quantity, BigDecimal unitPrice) {
            this.name = name;
            this.specs = specs;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        boolean isBlank() {
            return (name == null || name.isBlank()) && (specs == null || specs.isBlank())
                    && quantity == null && unitPrice == null;
        }

        /** A real row whose quantity or price still needs the seller. Used to highlight the field. */
        public boolean isNamed() {
            return name != null && !name.isBlank();
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSpecs() { return specs; }
        public void setSpecs(String specs) { this.specs = specs; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    }
}
