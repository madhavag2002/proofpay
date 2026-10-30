package dev.proofpay.proofpay.agent;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * One seller's chat with the assistant, kept in their browser session.
 * Holds the messages in OpenAI's format, the lines shown on screen, and any action awaiting confirmation.
 */
public class AgentConversation {

    static final int MAX_MESSAGES = 60;

    private final List<ObjectNode> messages = new ArrayList<>();
    private final List<ChatLine> lines = new ArrayList<>();
    private PendingAction pending;

    void addUser(String text) {
        trim();
        messages.add(JsonNodeFactory.instance.objectNode().put("role", "user").put("content", text));
        lines.add(new ChatLine(ChatLine.Kind.USER, text));
    }

    /** Stores only role, content and tool_calls, so OpenAI never gets fields back it did not expect. */
    void addAssistant(String content, ArrayNode toolCalls) {
        ObjectNode m = JsonNodeFactory.instance.objectNode().put("role", "assistant");
        if (content != null && !content.isBlank()) {
            m.put("content", content);
        } else {
            m.putNull("content");
        }
        if (toolCalls != null && !toolCalls.isEmpty()) {
            m.set("tool_calls", toolCalls);
        }
        messages.add(m);
    }

    void addToolResult(String callId, String content) {
        messages.add(JsonNodeFactory.instance.objectNode()
                .put("role", "tool")
                .put("tool_call_id", callId)
                .put("content", content));
    }

    void addLine(ChatLine.Kind kind, String text) {
        lines.add(new ChatLine(kind, text));
    }

    ArrayNode toModelMessages(String systemPrompt) {
        ArrayNode all = JsonNodeFactory.instance.arrayNode();
        all.addObject().put("role", "system").put("content", systemPrompt);
        messages.forEach(all::add);
        return all;
    }

    /** Drops the oldest messages, always restarting at a seller message so tool calls stay paired. */
    private void trim() {
        if (messages.size() <= MAX_MESSAGES) {
            return;
        }
        while (messages.size() > MAX_MESSAGES) {
            messages.remove(0);
        }
        while (!messages.isEmpty() && !"user".equals(messages.get(0).path("role").asString(""))) {
            messages.remove(0);
        }
    }

    void setPending(PendingAction action) { this.pending = action; }
    void clearPending() { this.pending = null; }

    public PendingAction getPending() { return pending; }
    public List<ChatLine> getLines() { return List.copyOf(lines); }
    List<ObjectNode> messages() { return List.copyOf(messages); }
}
