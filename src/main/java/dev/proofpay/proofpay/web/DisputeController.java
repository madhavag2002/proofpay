package dev.proofpay.proofpay.web;

import dev.proofpay.proofpay.ai.AiException;
import dev.proofpay.proofpay.dispute.CourierStatus;
import dev.proofpay.proofpay.dispute.DisputeCase;
import dev.proofpay.proofpay.dispute.DisputeService;
import dev.proofpay.proofpay.dispute.Playbook;
import dev.proofpay.proofpay.order.OrderService;
import dev.proofpay.proofpay.paypal.PayPalException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
class DisputeController {

    private final DisputeService disputes;
    private final OrderService orders;

    DisputeController(DisputeService disputes, OrderService orders) {
        this.disputes = disputes;
        this.orders = orders;
    }

    @GetMapping("/disputes")
    String list(Model model) {
        model.addAttribute("cases", disputes.listAll());
        return "disputes/list";
    }

    @PostMapping("/disputes/sync")
    String sync(RedirectAttributes redirect) {
        try {
            int n = disputes.syncAll();
            redirect.addFlashAttribute("notice", "Checked PayPal: " + n + " dispute(s) up to date.");
        } catch (PayPalException | IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/disputes";
    }

    @GetMapping("/disputes/paypal/{disputeId}")
    String byPayPalId(@PathVariable String disputeId) {
        return disputes.findByPayPalId(disputeId).map(c -> "redirect:/disputes/" + c.getId()).orElse("redirect:/disputes");
    }

    @GetMapping("/disputes/{id}")
    String detail(@PathVariable long id, Model model) {
        DisputeCase c = disputes.load(id);
        model.addAttribute("c", c);
        model.addAttribute("order", c.getOrderId() == null ? null : orders.load(c.getOrderId()));
        model.addAttribute("pack", disputes.evidenceFor(c));
        model.addAttribute("playbook", Playbook.forReason(c.getReason()));
        model.addAttribute("analysis", disputes.analysisOf(c));
        model.addAttribute("courierStatuses", CourierStatus.values());
        return "disputes/detail";
    }

    @PostMapping("/disputes/{id}/courier")
    String courier(@PathVariable long id, @RequestParam CourierStatus courierStatus, RedirectAttributes redirect) {
        disputes.setCourierStatus(id, courierStatus);
        redirect.addFlashAttribute("notice", "Courier status saved. Analyze again to use it.");
        return "redirect:/disputes/" + id;
    }

    @PostMapping("/disputes/{id}/proof")
    String proof(@PathVariable long id, @RequestParam("proof") MultipartFile file, RedirectAttributes redirect) {
        try {
            disputes.attachProof(id, file.getBytes(), file.getOriginalFilename(), file.getContentType());
            redirect.addFlashAttribute("notice", "Proof attached: " + file.getOriginalFilename());
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (IOException e) {
            redirect.addFlashAttribute("error", "Could not read the file. Try again.");
        }
        return "redirect:/disputes/" + id;
    }

    @PostMapping("/disputes/{id}/analyze")
    String analyze(@PathVariable long id, RedirectAttributes redirect) {
        try {
            disputes.analyze(id);
            redirect.addFlashAttribute("notice", "Analysis ready. Check the draft before sending it.");
        } catch (AiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/disputes/" + id;
    }

    @PostMapping("/disputes/{id}/draft")
    String draft(@PathVariable long id, @RequestParam String draftReply, RedirectAttributes redirect) {
        try {
            disputes.updateDraft(id, draftReply);
            redirect.addFlashAttribute("notice", "Draft saved.");
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/disputes/" + id;
    }

    @PostMapping("/disputes/{id}/submit")
    String submit(@PathVariable long id, RedirectAttributes redirect) {
        try {
            disputes.submit(id);
            redirect.addFlashAttribute("notice", "Your evidence was sent to PayPal.");
        } catch (PayPalException | IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/disputes/" + id;
    }

    @PostMapping("/disputes/{id}/refresh")
    String refresh(@PathVariable long id, RedirectAttributes redirect) {
        try {
            disputes.refresh(id);
            redirect.addFlashAttribute("notice", "Updated from PayPal.");
        } catch (PayPalException | IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/disputes/" + id;
    }
}
