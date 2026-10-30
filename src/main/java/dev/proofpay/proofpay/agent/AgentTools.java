package dev.proofpay.proofpay.agent;

import java.util.Set;

/** Which tools the assistant may use, and how. Anything not listed here is never offered to the AI. */
public final class AgentTools {

    public enum Kind { LOCAL, READ, WRITE, BLOCKED }

    /** ProofPay's own data, answered from the database. */
    public static final String PROOFPAY_ORDERS = "proofpay_orders";

    public static final String CREATE_TRACKING = "create_shipment_tracking";
    public static final String SEND_REMINDER = "send_invoice_reminder";

    /** Run straight away: they only read. */
    static final Set<String> READ = Set.of(
            "list_invoices", "get_invoice", "list_disputes", "get_dispute", "get_shipment_tracking");

    /** Change something in PayPal: run only after the seller clicks Confirm. */
    static final Set<String> WRITE = Set.of(CREATE_TRACKING, SEND_REMINDER);

    private AgentTools() {
    }

    public static Kind kindOf(String toolName) {
        if (PROOFPAY_ORDERS.equals(toolName)) {
            return Kind.LOCAL;
        }
        if (READ.contains(toolName)) {
            return Kind.READ;
        }
        if (WRITE.contains(toolName)) {
            return Kind.WRITE;
        }
        // accept_dispute_claim, create_refund, cancel_sent_invoice, create_invoice, orders, subscriptions...
        return Kind.BLOCKED;
    }

    public static boolean isOffered(String toolName) {
        return kindOf(toolName) != Kind.BLOCKED;
    }

    /** Words for the chat, e.g. "Checked PayPal: disputes". */
    public static String label(String toolName) {
        return switch (toolName) {
            case "list_invoices", "get_invoice" -> "invoices";
            case "list_disputes", "get_dispute" -> "disputes";
            case "get_shipment_tracking" -> "tracking";
            default -> toolName;
        };
    }
}
