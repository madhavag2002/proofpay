package dev.proofpay.proofpay.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Starts PayPal's MCP server, lists its tools, and makes one read-only call. */
@Component
@Profile("mcpspike")
class McpSpike implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(McpSpike.class);

    private final PayPalMcp mcp;

    McpSpike(PayPalMcp mcp) {
        this.mcp = mcp;
    }

    @Override
    public void run(String... args) {
        List<McpTool> tools = mcp.listTools();
        log.info("PayPal MCP offers {} tools:", tools.size());
        tools.forEach(t -> log.info("  {}", t.name()));

        // The schemas the agent will use in Steps 11-12: check their argument names.
        for (String name : List.of("list_invoices", "get_dispute", "create_shipment")) {
            tools.stream().filter(t -> t.name().equals(name)).findFirst()
                    .ifPresent(t -> log.info("Schema of {}: {}", name, t.inputSchema()));
        }

        McpResult invoices = mcp.callTool("list_invoices", Map.of());
        log.info("list_invoices (error={}):\n{}", invoices.error(), invoices.truncated(2000));

        McpResult disputes = mcp.callTool("list_disputes", Map.of());
        log.info("list_disputes (error={}):\n{}", disputes.error(), disputes.truncated(2000));

        mcp.close();
    }
}
