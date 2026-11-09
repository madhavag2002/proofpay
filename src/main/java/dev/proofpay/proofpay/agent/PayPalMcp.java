package dev.proofpay.proofpay.agent;

import dev.proofpay.proofpay.config.AppProperties;
import dev.proofpay.proofpay.paypal.PayPalClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Runs PayPal's official MCP server (@paypal/mcp) as a child process and talks to it over stdio.
 * Started on first use, and restarted before the PayPal token it was given expires.
 * This is the only class that uses the MCP SDK, so an SDK version change touches only this file.
 */
@Component
public class PayPalMcp implements McpTools {

    private static final Logger log = LoggerFactory.getLogger(PayPalMcp.class);

    /** Restart the server when its token has less than this left. */
    private static final Duration TOKEN_MARGIN = Duration.ofMinutes(10);

    private final AppProperties props;
    private final PayPalClient paypal;
    private final JsonMapper mapper;

    private McpSyncClient client;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public PayPalMcp(AppProperties props, PayPalClient paypal, JsonMapper mapper) {
        this.props = props;
        this.paypal = paypal;
        this.mapper = mapper;
    }

    /** The agents only run against the real PayPal sandbox. */
    @Override
    public boolean isAvailable() {
        return !props.paypal().isMock() && props.paypal().hasCredentials();
    }

    @Override
    public synchronized List<McpTool> listTools() {
        McpSyncClient c = ensureStarted();
        try {
            List<McpTool> tools = new ArrayList<>();
            for (McpSchema.Tool tool : c.listTools().tools()) {
                tools.add(new McpTool(tool.name(), tool.description(), mapper.valueToTree(tool.inputSchema())));
            }
            return tools;
        } catch (RuntimeException e) {
            throw new McpException("Could not list PayPal tools: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized McpResult callTool(String name, Map<String, Object> arguments) {
        McpSyncClient c = ensureStarted();
        McpSchema.CallToolResult result;
        try {
                result = c.callTool(McpSchema.CallToolRequest.builder(name)
                    .arguments(arguments)
                    .build());
        } catch (RuntimeException e) {
            throw new McpException("PayPal tool '" + name + "' failed: " + e.getMessage(), e);
        }
        StringBuilder text = new StringBuilder();
        if (result.content() != null) {
            for (McpSchema.Content content : result.content()) {
                if (content instanceof McpSchema.TextContent t) {
                    text.append(t.text()).append('\n');
                }
            }
        }
        return new McpResult(text.toString().strip(), Boolean.TRUE.equals(result.isError()));
    }

    private McpSyncClient ensureStarted() {
        if (!isAvailable()) {
            throw new McpException("The PayPal assistant needs PAYPAL_MODE=live and sandbox credentials.");
        }
        if (client != null && Instant.now().isBefore(tokenExpiresAt.minus(TOKEN_MARGIN))) {
            return client;
        }
        close(); // first start, or the old token is about to expire

        String token = paypal.accessToken();
        tokenExpiresAt = paypal.tokenExpiresAt();

        List<String> command = commandLine(props.paypal().mcpCommand());
        ServerParameters params = ServerParameters.builder(command.get(0))
                .args(command.subList(1, command.size()))
                .env(Map.of(
                        "PAYPAL_ACCESS_TOKEN", token,
                        "PAYPAL_ENVIRONMENT", "SANDBOX"))
                .build();

        try {
            // SDK CHECK 2: on some versions the transport is new StdioClientTransport(params)
            //   or new StdioClientTransport(params, McpJsonMapper.getDefault()).
            StdioClientTransport transport = new StdioClientTransport(params, McpJsonDefaults.getMapper());
            McpSyncClient started = McpClient.sync(transport)
                    // npx takes over 20 s to start the server, and longer when it has to download it.
                    .initializationTimeout(Duration.ofMinutes(2))
                    .requestTimeout(Duration.ofSeconds(60))
                    .build();
            started.initialize();
            client = started;
            log.info("PayPal MCP server started ({})", String.join(" ", command));
            return client;
        } catch (RuntimeException e) {
            throw new McpException("Could not start PayPal's MCP server. Is Node.js 18+ installed? " + e.getMessage(), e);
        }
    }

    /** Splits the command. On Windows, npx is a .cmd script, so it runs through cmd.exe. */
    static List<String> commandLine(String configured) {
        String cmd = configured == null || configured.isBlank() ? "npx -y @paypal/mcp --tools=all" : configured;
        List<String> parts = new ArrayList<>(Arrays.asList(cmd.strip().split("\\s+")));
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            parts.addAll(0, List.of("cmd.exe", "/c"));
        }
        return parts;
    }

    @PreDestroy
    public synchronized void close() {
        if (client != null) {
            try {
                client.closeGracefully();
            } catch (RuntimeException e) {
                log.debug("MCP client did not close cleanly: {}", e.getMessage());
            }
            client = null;
        }
    }
}
