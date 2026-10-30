package dev.proofpay.proofpay.web;

import dev.proofpay.proofpay.agent.AgentConversation;
import dev.proofpay.proofpay.agent.AssistantService;
import dev.proofpay.proofpay.agent.McpException;
import dev.proofpay.proofpay.ai.AiException;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class AssistantController {

    private static final String SESSION_KEY = "assistant";
    private static final int MAX_MESSAGE = 2000;

    private final AssistantService assistant;

    AssistantController(AssistantService assistant) {
        this.assistant = assistant;
    }

    @GetMapping("/assistant")
    String page(HttpSession session, @RequestParam(required = false) String prompt, Model model) {
        model.addAttribute("conversation", conversation(session));
        model.addAttribute("available", assistant.isAvailable());
        model.addAttribute("prompt", prompt);
        return "assistant";
    }

    @PostMapping("/assistant/message")
    String message(@RequestParam String text, HttpSession session, RedirectAttributes redirect) {
        String clean = text == null ? "" : text.strip();
        if (clean.isEmpty()) {
            return "redirect:/assistant";
        }
        if (clean.length() > MAX_MESSAGE) {
            clean = clean.substring(0, MAX_MESSAGE);
        }
        String message = clean;
        return run(redirect, () -> assistant.send(conversation(session), message));
    }

    @PostMapping("/assistant/confirm")
    String confirm(HttpSession session, RedirectAttributes redirect) {
        return run(redirect, () -> assistant.confirm(conversation(session)));
    }

    @PostMapping("/assistant/cancel")
    String cancel(HttpSession session, RedirectAttributes redirect) {
        return run(redirect, () -> assistant.cancel(conversation(session)));
    }

    @PostMapping("/assistant/reset")
    String reset(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
        return "redirect:/assistant";
    }

    private String run(RedirectAttributes redirect, Runnable action) {
        if (!assistant.isAvailable()) {
            redirect.addFlashAttribute("error", "The assistant needs PAYPAL_MODE=live and AI_MODE=live.");
            return "redirect:/assistant";
        }
        try {
            action.run();
        } catch (AiException | McpException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/assistant";
    }

    private static AgentConversation conversation(HttpSession session) {
        Object existing = session.getAttribute(SESSION_KEY);
        if (existing instanceof AgentConversation c) {
            return c;
        }
        AgentConversation created = new AgentConversation();
        session.setAttribute(SESSION_KEY, created);
        return created;
    }
}
