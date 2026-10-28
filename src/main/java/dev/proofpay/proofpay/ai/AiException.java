package dev.proofpay.proofpay.ai;

/** The AI call failed or returned something unusable. Safe to show the seller; never contains the key. */
public class AiException extends RuntimeException {

    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
