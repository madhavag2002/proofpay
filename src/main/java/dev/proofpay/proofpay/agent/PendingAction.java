package dev.proofpay.proofpay.agent;

import tools.jackson.databind.node.ObjectNode;

/** A write the AI asked for, waiting for the seller's Confirm or Cancel. */
public record PendingAction(String callId, String toolName, ObjectNode arguments, String summary) {

    public String argumentsText() {
        return arguments.toString();
    }
}
