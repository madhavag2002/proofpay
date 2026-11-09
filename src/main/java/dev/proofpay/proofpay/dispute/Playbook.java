package dev.proofpay.proofpay.dispute;

/**
 * One playbook per PayPal dispute reason code: how deep ProofPay goes, which PayPal evidence type
 * fits, and what decides the case. Codes and evidence types follow PayPal's reasons-and-evidence page.
 */
public enum Playbook {

    NOT_RECEIVED("MERCHANDISE_OR_SERVICE_NOT_RECEIVED", Depth.DEEP, "PROOF_OF_FULFILLMENT", """
            The buyer says the item never arrived. What decides it: tracking on file, what the courier says,
            and time since payment compared with the seller's usual delivery time to that country.
            - Tracking shows delivered: strong. Fight with the tracking number and delivery status.
            - Tracking exists and the parcel is still within the usual delivery time: reply with the tracking
              and the expected delivery date; do not refund yet.
            - No tracking, or overdue with no delivery: weak. Suggest checking with the courier, and a refund
              if the parcel is lost."""),

    NOT_AS_DESCRIBED("MERCHANDISE_OR_SERVICE_NOT_AS_DESCRIBED", Depth.DEEP, "OTHER", """
            The buyer says the item differs from what was agreed. What decides it: the agreed terms on the
            PayPal invoice the buyer paid, each item's agreed details, and what the buyer accepted in the chat
            (for example handmade variation).
            - The complaint matches something the buyer accepted or the terms allow: strong. Quote the terms.
            - The complaint is about a detail the terms do not cover: medium or weak.
            - The item clearly differs from the agreed terms: suggest a partial refund or a return."""),

    UNAUTHORISED("UNAUTHORISED", Depth.LIGHT, "PROOF_OF_FULFILLMENT",
            "The buyer says they did not make this payment. Useful: the invoice went to the buyer's own PayPal "
                    + "email, the ship-to address came from their chat, and tracking to that address."),

    INCORRECT_AMOUNT("INCORRECT_AMOUNT", Depth.LIGHT, "OTHER",
            "The buyer says the amount was wrong. Useful: the agreed prices and total on the invoice they paid."),

    DUPLICATE("DUPLICATE_TRANSACTION", Depth.LIGHT, "OTHER",
            "The buyer says they were charged twice. Useful: there is one invoice and one payment for this order."),

    PAID_ANOTHER_WAY("PAYMENT_BY_OTHER_MEANS", Depth.LIGHT, "OTHER",
            "The buyer says they already paid another way. Useful: payment records; the seller should check "
                    + "other payment channels."),

    CREDIT_NOT_PROCESSED("CREDIT_NOT_PROCESSED", Depth.LIGHT, "OTHER",
            "The buyer says a promised refund never came. Useful: what was promised in the chat; the seller "
                    + "may need to supply a refund id."),

    RECURRING("CANCELED_RECURRING_BILLING", Depth.NOT_APPLICABLE, "OTHER",
            "ProofPay sellers do not use subscriptions. Summarise and suggest handling it in PayPal."),

    OTHER("OTHER", Depth.LIGHT, "OTHER",
            "Summarise the claim, list the evidence found and what is missing.");

    public enum Depth { DEEP, LIGHT, NOT_APPLICABLE }

    private final String reasonCode;
    private final Depth depth;
    private final String evidenceType;
    private final String guidance;

    Playbook(String reasonCode, Depth depth, String evidenceType, String guidance) {
        this.reasonCode = reasonCode;
        this.depth = depth;
        this.evidenceType = evidenceType;
        this.guidance = guidance;
    }

    public static Playbook forReason(String code) {
        for (Playbook p : values()) {
            if (p.reasonCode.equals(code)) {
                return p;
            }
        }
        return OTHER;
    }

    /** PayPal needs tracking for proof of fulfilment; without it, send the notes as OTHER evidence. */
    public String evidenceTypeFor(boolean hasTracking) {
        return "PROOF_OF_FULFILLMENT".equals(evidenceType) && !hasTracking ? "OTHER" : evidenceType;
    }

    public String reasonCode() { return reasonCode; }
    public Depth depth() { return depth; }
    public String guidance() { return guidance; }
}
