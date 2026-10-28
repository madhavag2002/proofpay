package dev.proofpay.proofpay.paypal;

import dev.proofpay.proofpay.order.SellerOrder;

/** Sends invoices: the real PayPal sandbox, or a mock so the app runs with PAYPAL_MODE=mock. */
public interface InvoiceGateway {

    /** Creates a PayPal invoice carrying the agreed terms and sends it to the buyer. */
    SentInvoice createAndSend(SellerOrder order, String agreedTerms);

    boolean isMock();

    record SentInvoice(String invoiceId, String invoiceUrl) {
    }
}
