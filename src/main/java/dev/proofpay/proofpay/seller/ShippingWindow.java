package dev.proofpay.proofpay.seller;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** How long this seller's deliveries usually take to one destination, in days after payment. */
@Embeddable
public class ShippingWindow {

    /** Two-letter country code such as US, or "*" for everywhere else. */
    @Column(nullable = false, length = 2)
    private String destination;

    @Column(nullable = false)
    private int minDays;

    @Column(nullable = false)
    private int maxDays;

    protected ShippingWindow() {
    }

    public ShippingWindow(String destination, int minDays, int maxDays) {
        String d = destination == null ? "" : destination.strip().toUpperCase(Locale.ROOT);
        if (!d.equals("*") && !d.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("Destination must be a 2-letter country code like US, or * for everywhere else");
        }
        if (minDays < 1 || maxDays < minDays || maxDays > 120) {
            throw new IllegalArgumentException("Delivery days must look like 12-20, with the smaller number first");
        }
        this.destination = d;
        this.minDays = minDays;
        this.maxDays = maxDays;
    }

    /** Reads "US=12-20,GB=10-18,*=15-25". A plain "12-20" means every destination. */
    public static List<ShippingWindow> parse(String spec) {
        if (spec == null || spec.isBlank()) {
            throw new IllegalArgumentException("Enter at least one delivery time, e.g. 12-20");
        }
        List<ShippingWindow> windows = new ArrayList<>();
        for (String part : spec.split(",")) {
            String p = part.strip();
            if (p.isEmpty()) {
                continue;
            }
            String dest = "*";
            String range = p;
            int eq = p.indexOf('=');
            if (eq >= 0) {
                dest = p.substring(0, eq);
                range = p.substring(eq + 1);
            }
            String[] bounds = range.strip().split("-");
            try {
                int min = Integer.parseInt(bounds[0].strip());
                int max = bounds.length > 1 ? Integer.parseInt(bounds[1].strip()) : min;
                windows.add(new ShippingWindow(dest, min, max));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Could not read '" + p + "'. Use a form like US=12-20");
            }
        }
        return windows;
    }

    public boolean isDefault() {
        return "*".equals(destination);
    }

    public String describe() {
        return (isDefault() ? "anywhere else" : destination) + ": " + minDays + "-" + maxDays + " days";
    }

    public String getDestination() { return destination; }
    public int getMinDays() { return minDays; }
    public int getMaxDays() { return maxDays; }
}
