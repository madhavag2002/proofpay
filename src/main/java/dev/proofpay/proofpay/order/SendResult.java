package dev.proofpay.proofpay.order;

/** What happened when the seller clicked "Approve and send". */
public record SendResult(Outcome outcome, ApprovalCheck check, String message) {

    public enum Outcome {
        SENT,                // invoice created and sent
        MISSING_FIELDS,      // required fields are empty; nothing sent
        NEEDS_CONFIRMATION,  // a HIGH risk is open; the seller must choose "Send anyway"
        ALREADY_SENT,        // this order was approved before
        BUSY,                // the same order is being sent right now (double click)
        PAYPAL_ERROR         // PayPal refused; the order stays a draft
    }

    public boolean isSent() {
        return outcome == Outcome.SENT;
    }

    static SendResult sent(ApprovalCheck check) {
        return new SendResult(Outcome.SENT, check, "Invoice sent to the buyer with the agreed terms.");
    }

    static SendResult missingFields(ApprovalCheck check) {
        return new SendResult(Outcome.MISSING_FIELDS, check, "Fill in the missing details before sending.");
    }

    static SendResult needsConfirmation(ApprovalCheck check) {
        return new SendResult(Outcome.NEEDS_CONFIRMATION, check,
                "A high dispute risk is still open. Settle it with the buyer, or choose Send anyway.");
    }

    static SendResult alreadySent() {
        return new SendResult(Outcome.ALREADY_SENT, null, "This order was already approved.");
    }

    static SendResult busy() {
        return new SendResult(Outcome.BUSY, null, "This order is being sent. Refresh in a moment.");
    }

    static SendResult paypalError(ApprovalCheck check, String detail) {
        return new SendResult(Outcome.PAYPAL_ERROR, check, "PayPal did not accept the invoice. " + detail);
    }
}
