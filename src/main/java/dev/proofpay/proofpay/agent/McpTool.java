package dev.proofpay.proofpay.agent;

import tools.jackson.databind.JsonNode;

/** One tool offered by the MCP server: name, description, and the JSON schema of its arguments. */
public record McpTool(String name, String description, JsonNode inputSchema) {
}
