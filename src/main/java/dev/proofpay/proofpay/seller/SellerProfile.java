package dev.proofpay.proofpay.seller;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** One seller's settings. Today there is one per deployment; a multi-seller version adds one row per seller. */
@Entity
public class SellerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "seller_shipping_window", joinColumns = @JoinColumn(name = "seller_id"))
    @OrderColumn(name = "sort_index")
    private List<ShippingWindow> shippingWindows = new ArrayList<>();

    protected SellerProfile() {
    }

    public SellerProfile(List<ShippingWindow> windows) {
        replaceShippingWindows(windows);
    }

    public void replaceShippingWindows(List<ShippingWindow> windows) {
        if (windows == null || windows.isEmpty()) {
            throw new IllegalArgumentException("Enter at least one delivery time");
        }
        Set<String> seen = new HashSet<>();
        for (ShippingWindow w : windows) {
            if (!seen.add(w.getDestination())) {
                throw new IllegalArgumentException("Destination " + w.getDestination() + " is listed twice");
            }
        }
        this.shippingWindows = new ArrayList<>(windows);
    }

    /** The window for a country, falling back to the "*" window. Empty if neither exists. */
    public Optional<ShippingWindow> windowFor(String countryCode) {
        String code = countryCode == null ? "" : countryCode.strip().toUpperCase(Locale.ROOT);
        return shippingWindows.stream().filter(w -> w.getDestination().equals(code)).findFirst()
                .or(() -> shippingWindows.stream().filter(ShippingWindow::isDefault).findFirst());
    }

    /** For the AI prompt and the Settings page, e.g. "US: 12-20 days; anywhere else: 15-25 days". */
    public String describeShipping() {
        return shippingWindows.stream().map(ShippingWindow::describe).collect(Collectors.joining("; "));
    }

    public Long getId() { return id; }
    public List<ShippingWindow> getShippingWindows() { return List.copyOf(shippingWindows); }
}
