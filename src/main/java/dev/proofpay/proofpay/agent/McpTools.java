package dev.proofpay.proofpay.agent;

import java.util.List;
import java.util.Map;

/** What the assistant needs from PayPal's MCP server. PayPalMcp is the real one; tests use a fake. */
public interface McpTools {

    boolean isAvailable();

    List<McpTool> listTools();

    McpResult callTool(String name, Map<String, Object> arguments);
}
