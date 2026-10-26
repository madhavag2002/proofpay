package dev.proofpay.proofpay.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

/** One line of an order, exactly as agreed with the buyer. */
@Embeddable
public class OrderItem {

    @Column(nullable = false, length = 200)
    private String name;

    /** Size, colour, material, finish: the details a buyer could later dispute. */
    @Column(length = 1000)
    private String specs;

    @Column(nullable = false)
    private int quantity;

    /** Null until the seller sets it. The AI never invents a price. */
    @Column(precision = 12, scale = 2)
    private BigDecimal unitPrice;

    protected OrderItem() {
    }

    public OrderItem(String name, String specs, int quantity, BigDecimal unitPrice) {
        // Column sizes match PayPal's limits for item name (200) and description (1000).
        this.name = clip(name, 200);
        this.specs = clip(specs, 1000);
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public BigDecimal lineTotal() {
        return unitPrice == null ? null : unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    static String clip(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    public String getName() { return name; }
    public String getSpecs() { return specs; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
}
