package dev.proofpay.proofpay.paypal;

import dev.proofpay.proofpay.order.SellerOrder;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Real gateway: create, send, and look up the buyer's pay link in the PayPal sandbox. */
public class PayPalInvoiceGateway implements InvoiceGateway {

    private final PayPalClient client;
    private final InvoicePayloadBuilder builder;
    private final String sellerName;

    public PayPalInvoiceGateway(PayPalClient client, InvoicePayloadBuilder builder, String sellerName) {
        this.client = client;
        this.builder = builder;
        this.sellerName = sellerName;
    }

    @Override
    public SentInvoice createAndSend(SellerOrder order, String agreedTerms) {
        ObjectNode payload = builder.build(order, agreedTerms, sellerName);
        // Same order + same content = same request id, so a retry after a timeout reuses the same invoice
        // instead of creating a second one. Edited content gets a new id.
        String requestId = "proofpay-" + order.getId() + "-" + shortHash(payload.toString());
        String invoiceId = client.createDraftInvoice(payload, requestId);
        client.sendInvoice(invoiceId);

        String url;
        try {
            url = client.recipientViewUrl(invoiceId);
        } catch (PayPalException e) {
            url = null; // the invoice is already sent; a missing link must not undo that
        }
        return new SentInvoice(invoiceId, url);
    }

    @Override
    public boolean isMock() {
        return false;
    }

    static String shortHash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
