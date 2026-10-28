package dev.proofpay.proofpay.paypal;

import dev.proofpay.proofpay.order.SellerOrder;

import java.util.UUID;

/** Stands in for PayPal when PAYPAL_MODE=mock. Nothing leaves the machine. */
public class MockInvoiceGateway implements InvoiceGateway {

    @Override
    public SentInvoice createAndSend(SellerOrder order, String agreedTerms) {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new SentInvoice("MOCK-INV-" + order.getId() + "-" + suffix, null);
    }

    @Override
    public boolean isMock() {
        return true;
    }
}
