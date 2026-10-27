package dev.proofpay.proofpay.paypal;

/** A PayPal call failed. The message is safe to show; it never contains credentials. */
public class PayPalException extends RuntimeException {

    public PayPalException(String message) {
        super(message);
    }

    public PayPalException(String message, Throwable cause) {
        super(message, cause);
    }
}
