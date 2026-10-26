package dev.proofpay.proofpay.order;

public enum OrderStatus {
    DRAFT,          // AI draft exists; nothing sent to PayPal yet
    INVOICE_SENT,   // seller approved; invoice with agreed terms sent
    PAID,           // PayPal reported payment
    DISPUTED        // buyer opened a dispute
}
