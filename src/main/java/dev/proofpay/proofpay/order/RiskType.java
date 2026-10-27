package dev.proofpay.proofpay.order;

/** Things in a chat that commonly lead to PayPal disputes later. */
public enum RiskType {
    VAGUE_SPEC,                  // "blue-ish", "like the photo", no size
    VARIATION_NOT_ACKNOWLEDGED,  // handmade goods, buyer never accepted variation
    UNREALISTIC_DELIVERY,        // deadline shorter than the seller's usual shipping time
    UNCONFIRMED_REQUEST,         // buyer asked for something the seller never confirmed
    MISSING_PRICE,
    OTHER
}
