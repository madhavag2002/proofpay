package dev.proofpay.proofpay.agent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

/** The AI behind the assistant: given the conversation and tools, returns its next message. */
public interface AgentModel {

    /** Returns the assistant message: "content" and optionally "tool_calls", in OpenAI's format. */
    JsonNode next(ArrayNode messages, ArrayNode tools);
}
