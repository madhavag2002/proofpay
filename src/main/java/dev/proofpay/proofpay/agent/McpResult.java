package dev.proofpay.proofpay.agent;

/** What a tool call returned: its text output, and whether the server marked it as an error. */
public record McpResult(String text, boolean error) {

    /** Long results are cut before they go to the AI, to keep requests small. */
    public String truncated(int maxChars) {
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "\n...(truncated)";
    }
}
