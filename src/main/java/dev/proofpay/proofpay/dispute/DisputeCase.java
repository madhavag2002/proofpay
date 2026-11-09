package dev.proofpay.proofpay.dispute;

import jakarta.persistence.*;

import java.time.Instant;

/** A PayPal dispute as ProofPay tracks it: PayPal's facts, the linked order, the analysis and the response. */
@Entity
public class DisputeCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @Column(nullable = false, unique = true, length = 64)
    private String disputeId;

    private Long orderId;

    @Column(length = 60) private String reason;
    @Column(length = 40) private String stage;
    @Column(length = 40) private String status;
    @Column(length = 40) private String sellerResponseDue;
    @Column(length = 20) private String amount;
    @Column(length = 3) private String currency;
    @Column(length = 64) private String sellerTransactionId;
    @Column(length = 64) private String invoiceId;
    @Column(length = 200) private String allowedResponses;
    private boolean sellerProtectionEligible;
    @Column(length = 2000) private String buyerClaim;
    @Column(length = 20000) private String paypalJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourierStatus courierStatus = CourierStatus.UNKNOWN;

    @Enumerated(EnumType.STRING) @Column(length = 10) private CaseStrength strength;
    @Enumerated(EnumType.STRING) @Column(length = 20) private Recommendation recommendation;
    @Column(length = 20000) private String analysisJson;
    @Column(length = 4000) private String draftReply;
    @Column(length = 1000) private String submitError;

    @Column(nullable = false) private Instant createdAt = Instant.now();
    private Instant updatedAt;
    private Instant analyzedAt;
    private Instant submittedAt;

    protected DisputeCase() {
    }

    public DisputeCase(String disputeId) {
        this.disputeId = disputeId;
    }

    void updateFromPayPal(String reason, String stage, String status, String due, String amount, String currency,
                          String sellerTransactionId, String invoiceId, String allowedResponses,
                          boolean protectionEligible, String buyerClaim, String paypalJson) {
        this.reason = reason;
        this.stage = stage;
        this.status = status;
        this.sellerResponseDue = due;
        this.amount = amount;
        this.currency = currency;
        this.sellerTransactionId = sellerTransactionId;
        this.invoiceId = invoiceId;
        this.allowedResponses = allowedResponses;
        this.sellerProtectionEligible = protectionEligible;
        this.buyerClaim = buyerClaim;
        this.paypalJson = paypalJson;
        this.updatedAt = Instant.now();
    }

    void linkOrder(Long orderId) {
        if (this.orderId == null) {
            this.orderId = orderId;
        }
    }

    void setCourierStatus(CourierStatus courierStatus) {
        this.courierStatus = courierStatus;
    }

    void recordAnalysis(CaseStrength strength, Recommendation recommendation, String analysisJson, String draftReply) {
        this.strength = strength;
        this.recommendation = recommendation;
        this.analysisJson = analysisJson;
        this.draftReply = draftReply;
        this.analyzedAt = Instant.now();
    }

    void updateDraft(String text) {
        if (submittedAt != null) {
            throw new IllegalStateException("The response was already sent to PayPal.");
        }
        this.draftReply = text;
    }

    void markSubmitted() {
        this.submittedAt = Instant.now();
        this.submitError = null;
    }

    void recordSubmitError(String message) {
        this.submitError = message;
    }

    /** PayPal only accepts seller evidence while it is waiting for the seller. */
    public boolean canSubmit() {
        return "WAITING_FOR_SELLER_RESPONSE".equals(status) && submittedAt == null
                && draftReply != null && !draftReply.isBlank();
    }

    public Long getId() { return id; }
    public String getDisputeId() { return disputeId; }
    public Long getOrderId() { return orderId; }
    public String getReason() { return reason; }
    public String getStage() { return stage; }
    public String getStatus() { return status; }
    public String getSellerResponseDue() { return sellerResponseDue; }
    public String getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getSellerTransactionId() { return sellerTransactionId; }
    public String getInvoiceId() { return invoiceId; }
    public String getAllowedResponses() { return allowedResponses; }
    public boolean isSellerProtectionEligible() { return sellerProtectionEligible; }
    public String getBuyerClaim() { return buyerClaim; }
    public String getPaypalJson() { return paypalJson; }
    public CourierStatus getCourierStatus() { return courierStatus; }
    public CaseStrength getStrength() { return strength; }
    public Recommendation getRecommendation() { return recommendation; }
    public String getAnalysisJson() { return analysisJson; }
    public String getDraftReply() { return draftReply; }
    public String getSubmitError() { return submitError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getAnalyzedAt() { return analyzedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
}
