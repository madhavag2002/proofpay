package dev.proofpay.proofpay.web;

import dev.proofpay.proofpay.seller.SellerProfile;
import dev.proofpay.proofpay.seller.SellerProfileService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.stream.Collectors;

/** The seller's delivery times per destination, used by the delivery-deadline check. */
@Controller
class SettingsController {

    private final SellerProfileService profiles;

    SettingsController(SellerProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/settings")
    String show(Model model) {
        SellerProfile profile = profiles.current();
        model.addAttribute("windows", profile.getShippingWindows());
        model.addAttribute("spec", toSpec(profile));
        return "settings";
    }

    @PostMapping("/settings/shipping")
    String saveShipping(@RequestParam String spec, Model model, RedirectAttributes redirect) {
        try {
            profiles.updateShipping(spec);
            redirect.addFlashAttribute("notice", "Delivery times saved. New orders are checked against them.");
            return "redirect:/settings";
        } catch (IllegalArgumentException e) {
            model.addAttribute("windows", profiles.current().getShippingWindows());
            model.addAttribute("spec", spec);
            model.addAttribute("error", e.getMessage());
            return "settings";
        }
    }

    static String toSpec(SellerProfile profile) {
        return profile.getShippingWindows().stream()
                .map(w -> w.getDestination() + "=" + w.getMinDays() + "-" + w.getMaxDays())
                .collect(Collectors.joining(","));
    }
}
