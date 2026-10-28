package dev.proofpay.proofpay.web;

import dev.proofpay.proofpay.ai.AiException;
import dev.proofpay.proofpay.order.ApprovalCheck;
import dev.proofpay.proofpay.order.OrderService;
import dev.proofpay.proofpay.order.OrderStatus;
import dev.proofpay.proofpay.order.SellerOrder;
import dev.proofpay.proofpay.order.SendResult;
import dev.proofpay.proofpay.paypal.InvoiceGateway;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

@Controller
class OrderController {

    static final int NO_TRACKING_DAYS = 3;
    static final List<String> CURRENCIES = List.of("USD", "EUR", "GBP", "AUD", "CAD", "SGD", "AED", "JPY");
    private static final String NUMBER_ERROR = "Quantities must be whole numbers and prices must be numbers, like 24.50.";

    private final OrderService orders;
    private final InvoiceGateway gateway;
    private final Clock clock;

    OrderController(OrderService orders, InvoiceGateway gateway, Clock clock) {
        this.orders = orders;
        this.gateway = gateway;
        this.clock = clock;
    }

    @GetMapping("/")
    String home() {
        return "redirect:/orders";
    }

    // ---- List

    @GetMapping("/orders")
    String list(Model model) {
        model.addAttribute("orders", orders.listAll());
        model.addAttribute("noTracking", orders.paidWithoutTracking(NO_TRACKING_DAYS));
        model.addAttribute("noTrackingDays", NO_TRACKING_DAYS);
        return "orders/list";
    }

    // ---- New order

    @GetMapping("/orders/new")
    String newOrder(Model model) {
        model.addAttribute("form", new NewOrderForm());
        model.addAttribute("currencies", CURRENCIES);
        return "orders/new";
    }

    @PostMapping("/orders")
    String create(@Valid @ModelAttribute("form") NewOrderForm form, BindingResult result, Model model) {
        model.addAttribute("currencies", CURRENCIES);
        if (result.hasErrors()) {
            return "orders/new";
        }
        try {
            SellerOrder order = orders.createFromChat(
                    form.getBuyerName(), form.getBuyerEmail(), form.getCurrency(), form.toChatInput());
            return "redirect:/orders/" + order.getId();
        } catch (AiException | IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
        } catch (IOException e) {
            model.addAttribute("error", "Could not read the uploaded file. Try again.");
        }
        return "orders/new";
    }

    // ---- Review (draft) or detail (sent)

    @GetMapping("/orders/{id}")
    String show(@PathVariable long id, Model model) {
        SellerOrder order = orders.load(id);
        model.addAttribute("order", order);
        if (order.getStatus() == OrderStatus.DRAFT) {
            model.addAttribute("draft", DraftForm.from(order));
            model.addAttribute("check", ApprovalCheck.of(order));
            return "orders/review";
        }
        model.addAttribute("noTrackingAlert", needsTracking(order));
        model.addAttribute("noTrackingDays", NO_TRACKING_DAYS);
        return "orders/detail";
    }

    @PostMapping("/orders/{id}/save")
    String save(@PathVariable long id, @ModelAttribute("draft") DraftForm draft, BindingResult binding,
                Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return review(id, draft, model, NUMBER_ERROR, null, false);
        }
        try {
            orders.saveDraft(id, draft.toEdit());
            redirect.addFlashAttribute("notice", "Draft saved. Come back when the buyer has answered.");
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("error", "This order was already sent, so it can no longer be edited.");
        }
        return "redirect:/orders/" + id;
    }

    /** Saves the current edits first, so marking a risk handled never loses typing. */
    @PostMapping("/orders/{id}/flags/{index}/resolve")
    String resolve(@PathVariable long id, @PathVariable int index, @ModelAttribute("draft") DraftForm draft,
                   BindingResult binding, Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return review(id, draft, model, NUMBER_ERROR, null, false);
        }
        try {
            orders.saveDraft(id, draft.toEdit());
            orders.resolveFlag(id, index);
            redirect.addFlashAttribute("notice", "Risk marked as handled.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirect.addFlashAttribute("error", "That risk could not be updated. Refresh the page and try again.");
        }
        return "redirect:/orders/" + id;
    }

    @PostMapping("/orders/{id}/approve")
    String approve(@PathVariable long id, @ModelAttribute("draft") DraftForm draft, BindingResult binding,
                   Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return review(id, draft, model, NUMBER_ERROR, null, false);
        }
        SendResult result = orders.approveAndSend(id, draft.toEdit(), draft.isSendAnyway());
        return switch (result.outcome()) {
            case SENT -> {
                redirect.addFlashAttribute("notice", result.message());
                yield "redirect:/orders/" + id;
            }
            case ALREADY_SENT, BUSY -> {
                redirect.addFlashAttribute("error", result.message());
                yield "redirect:/orders/" + id;
            }
            case NEEDS_CONFIRMATION -> review(id, draft, model, result.message(), result.check(), true);
            case MISSING_FIELDS, PAYPAL_ERROR -> review(id, draft, model, result.message(), result.check(), false);
        };
    }

    /** Mock mode only: pretends the buyer paid, so the flow can be finished without PayPal. */
    @PostMapping("/orders/{id}/simulate-paid")
    String simulatePaid(@PathVariable long id, RedirectAttributes redirect) {
        if (!gateway.isMock()) {
            redirect.addFlashAttribute("error", "Simulated payments only work in mock mode.");
            return "redirect:/orders/" + id;
        }
        SellerOrder order = orders.load(id);
        if (order.getPaypalInvoiceId() != null) {
            orders.markPaidByInvoice(order.getPaypalInvoiceId(), "MOCK-PAYMENT-" + id);
            redirect.addFlashAttribute("notice", "Simulated: the buyer paid.");
        }
        return "redirect:/orders/" + id;
    }

    // ---- Helpers

    /** Re-shows the review page with the seller's typing kept and a message on top. */
    private String review(long id, DraftForm draft, Model model, String error, ApprovalCheck check,
                          boolean needsConfirmation) {
        SellerOrder order = orders.load(id);
        if (order.getStatus() != OrderStatus.DRAFT) {
            return "redirect:/orders/" + id;
        }
        draft.padSpareRows();
        model.addAttribute("order", order);
        model.addAttribute("draft", draft);
        model.addAttribute("check", check != null ? check : ApprovalCheck.of(order));
        model.addAttribute("error", error);
        model.addAttribute("needsConfirmation", needsConfirmation);
        return "orders/review";
    }

    private boolean needsTracking(SellerOrder order) {
        return order.getStatus() == OrderStatus.PAID
                && order.getTrackingNumber() == null
                && order.getPaidAt() != null
                && order.getPaidAt().isBefore(clock.instant().minus(Duration.ofDays(NO_TRACKING_DAYS)));
    }
}
