package dev.proofpay.proofpay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** All settings under proofpay.* in application.yml. Secrets come from .env or environment variables. */
@ConfigurationProperties(prefix = "proofpay")
public record AppProperties(
        String sellerName,
        String sellerPassword,
        String timeZone,
        /** Starting delivery times for a new seller profile, e.g. "US=12-20,*=15-25". */
        String shippingDays,
        PayPal paypal,
        Ai ai) {

    public enum Mode { MOCK, LIVE }

    public record PayPal(
            Mode mode,
            String baseUrl,
            String clientId,
            String clientSecret,
            String webhookId,
            boolean allowUnverifiedWebhooks,
            /** Command that starts PayPal's MCP server, e.g. "npx -y @paypal/mcp --tools=all". */
            String mcpCommand) {

        public boolean isMock() {
            return mode == Mode.MOCK;
        }

        public boolean hasCredentials() {
            return notBlank(clientId) && notBlank(clientSecret);
        }
    }

    public record Ai(
            Mode mode,
            String apiKey,
            String model,
            String baseUrl) {

        /** Mock if asked for, or if no key is set, so the app never fails just because a key is missing. */
        public boolean isMock() {
            return mode == Mode.MOCK || !notBlank(apiKey);
        }
    }

    public boolean passwordEnabled() {
        return notBlank(sellerPassword);
    }

    public String zoneOrDefault() {
        return notBlank(timeZone) ? timeZone : "Asia/Kolkata";
    }

    static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
