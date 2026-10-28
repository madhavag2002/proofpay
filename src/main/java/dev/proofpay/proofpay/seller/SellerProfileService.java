package dev.proofpay.proofpay.seller;

import dev.proofpay.proofpay.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Loads the seller's profile, creating it from SHIPPING_DAYS the first time. */
@Service
public class SellerProfileService {

    private static final Logger log = LoggerFactory.getLogger(SellerProfileService.class);

    private final SellerProfileRepository repository;
    private final AppProperties props;

    public SellerProfileService(SellerProfileRepository repository, AppProperties props) {
        this.repository = repository;
        this.props = props;
    }

    @Transactional
    public SellerProfile current() {
        return repository.findFirstByOrderByIdAsc()
                .orElseGet(() -> repository.save(new SellerProfile(defaultWindows())));
    }

    /** Used by the Settings page in Step 8. Throws IllegalArgumentException with a readable message. */
    @Transactional
    public SellerProfile updateShipping(String spec) {
        SellerProfile profile = current();
        profile.replaceShippingWindows(ShippingWindow.parse(spec));
        return profile;
    }

    private List<ShippingWindow> defaultWindows() {
        String spec = props.shippingDays() == null || props.shippingDays().isBlank() ? "12-20" : props.shippingDays();
        try {
            return ShippingWindow.parse(spec);
        } catch (IllegalArgumentException e) {
            log.warn("SHIPPING_DAYS '{}' is not valid ({}); using 12-20 days everywhere", spec, e.getMessage());
            return List.of(new ShippingWindow("*", 12, 20));
        }
    }
}
