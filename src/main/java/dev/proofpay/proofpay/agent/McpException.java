package dev.proofpay.proofpay.agent;

/** The PayPal MCP server could not be started or a tool call failed. Safe to show the seller. */
public class McpException extends RuntimeException {

    public McpException(String message) {
        super(message);
    }

    public McpException(String message, Throwable cause) {
        super(message, cause);
    }
}
