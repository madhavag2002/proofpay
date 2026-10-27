package dev.proofpay.proofpay.order;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** An order taken over chat: the buyer's words, the risks found, the approved terms, and the PayPal records. */
@Entity
public class SellerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Optimistic lock: two clicks at once cannot both change the order. */
    @Version
    private long version;

    // ---- The buyer and their chat

    @Column(nullable = false)
    private String buyerName;

    @Column(nullable = false)
    private String buyerEmail;

    /** The chat text: pasted, read from screenshots, or from a WhatsApp export. */
    @Column(nullable = false, length = 8000)
    private String buyerMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatSource chatSource = ChatSource.PASTE;

    private String buyerLanguage;

    @Column(length = 2000)
    private String englishSummary;

    // ---- What was agreed (extracted by AI, confirmed by the seller)

    @Column(nullable = false, length = 3)
    private String currency;

    private String shipTo;
    private String deliveryBy;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_item", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "sort_index")
    private List<OrderItem> items = new ArrayList<>();

    /** Things the buyer explicitly accepted in the chat, e.g. "handmade, small variations are fine". */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_accepted_note", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "sort_index")
    @Column(name = "note", length = 500)
    private List<String> acceptedNotes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_open_question", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "sort_index")
    @Column(name = "question", length = 500)
    private List<String> openQuestions = new ArrayList<>();

    // ---- Prevention: dispute risks found in the chat

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_risk_flag", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "sort_index")
    private List<RiskFlag> riskFlags = new ArrayList<>();

    /** Suggested message for the seller to send the buyer before invoicing. */
    @Column(length = 2000)
    private String clarificationMessage;

    // ---- PayPal records

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.DRAFT;

    /** The exact text written into the PayPal invoice terms on approval. */
    @Column(length = 4000)
    private String agreedTerms;

    @Column(unique = true)
    private String paypalInvoiceId;

    private String invoiceUrl;

    /** From the paid webhook; used to match disputes and add tracking. */
    private String paymentId;

    private String carrier;
    private String trackingNumber;

    @Column(unique = true)
    private String disputeId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    private Instant approvedAt;
    private Instant paidAt;
    private Instant disputedAt;

    protected SellerOrder() {
    }

    public SellerOrder(String buyerName, String buyerEmail, String buyerMessage, String currency) {
        this.buyerName = buyerName;
        this.buyerEmail = buyerEmail;
        this.buyerMessage = OrderItem.clip(buyerMessage, 8000);
        this.currency = currency;
    }

    // ---- State changes. Each checks the current status, so steps can't be skipped or repeated.

    /** Saves the AI's reading of the chat. Only while drafting. */
    public void recordAiDraft(String language, String summary, List<OrderItem> items, String shipTo,
                              String deliveryBy, List<String> acceptedNotes, List<String> openQuestions) {
        requireStatus(OrderStatus.DRAFT);
        this.buyerLanguage = language;
        this.englishSummary = OrderItem.clip(summary, 2000);
        this.items = new ArrayList<>(items);
        this.shipTo = shipTo;
        this.deliveryBy = deliveryBy;
        this.acceptedNotes = new ArrayList<>(acceptedNotes);
        this.openQuestions = new ArrayList<>(openQuestions);
    }

    /** Saves the AI's dispute-risk check. Only while drafting. */
    public void recordRiskCheck(ChatSource source, List<RiskFlag> flags, String clarification) {
        requireStatus(OrderStatus.DRAFT);
        this.chatSource = Objects.requireNonNull(source);
        this.riskFlags = new ArrayList<>(flags);
        this.clarificationMessage = OrderItem.clip(clarification, 2000);
    }

    /** The seller marks a risk handled, e.g. after sending the clarification and updating the specs. */
    public void resolveFlag(int index) {
        requireStatus(OrderStatus.DRAFT);
        if (index < 0 || index >= riskFlags.size()) {
            throw new IllegalArgumentException("No risk flag at position " + index);
        }
        List<RiskFlag> updated = new ArrayList<>(riskFlags);
        updated.set(index, riskFlags.get(index).resolve());
        this.riskFlags = updated;
    }

    /** The seller's edits on the review page: this is where AI mistakes get fixed. Only while drafting. */
    public void updateDraft(List<OrderItem> items, String shipTo, String deliveryBy, List<String> acceptedNotes) {
        requireStatus(OrderStatus.DRAFT);
        this.items = new ArrayList<>(items);
        this.shipTo = shipTo;
        this.deliveryBy = deliveryBy;
        this.acceptedNotes = new ArrayList<>(acceptedNotes);
    }

    public void markInvoiceSent(String agreedTerms, String invoiceId, String invoiceUrl) {
        requireStatus(OrderStatus.DRAFT);
        this.agreedTerms = Objects.requireNonNull(agreedTerms);
        this.paypalInvoiceId = Objects.requireNonNull(invoiceId);
        this.invoiceUrl = invoiceUrl;
        this.approvedAt = Instant.now();
        this.status = OrderStatus.INVOICE_SENT;
    }

    /** @return false if already paid (PayPal resent the webhook), true if this call changed it. */
    public boolean markPaid(String paymentId) {
        if (status == OrderStatus.PAID || status == OrderStatus.DISPUTED) {
            return false;
        }
        requireStatus(OrderStatus.INVOICE_SENT);
        this.paymentId = paymentId;
        this.paidAt = Instant.now();
        this.status = OrderStatus.PAID;
        return true;
    }

    public void recordTracking(String carrier, String trackingNumber) {
        requireStatus(OrderStatus.PAID);
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
    }

    /** @return false if this dispute was already recorded. */
    public boolean markDisputed(String disputeId) {
        if (status == OrderStatus.DISPUTED) {
            return false;
        }
        requireStatus(OrderStatus.PAID);
        this.disputeId = Objects.requireNonNull(disputeId);
        this.disputedAt = Instant.now();
        this.status = OrderStatus.DISPUTED;
        return true;
    }

    private void requireStatus(OrderStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Order " + id + " is " + status + ", expected " + expected);
        }
    }

    // ---- Derived values

    /** Null while any item has no price. */
    public BigDecimal total() {
        BigDecimal sum = BigDecimal.ZERO;
        for (OrderItem item : items) {
            if (item.lineTotal() == null) {
                return null;
            }
            sum = sum.add(item.lineTotal());
        }
        return sum;
    }

    public boolean hasOpenHighRisk() {
        return riskFlags.stream().anyMatch(f -> f.getLevel() == RiskLevel.HIGH && !f.isResolved());
    }

    // ---- Getters (no setters on purpose)

    public Long getId() { return id; }
    public String getBuyerName() { return buyerName; }
    public String getBuyerEmail() { return buyerEmail; }
    public String getBuyerMessage() { return buyerMessage; }
    public ChatSource getChatSource() { return chatSource; }
    public String getBuyerLanguage() { return buyerLanguage; }
    public String getEnglishSummary() { return englishSummary; }
    public String getCurrency() { return currency; }
    public String getShipTo() { return shipTo; }
    public String getDeliveryBy() { return deliveryBy; }
    public List<OrderItem> getItems() { return List.copyOf(items); }
    public List<String> getAcceptedNotes() { return List.copyOf(acceptedNotes); }
    public List<String> getOpenQuestions() { return List.copyOf(openQuestions); }
    public List<RiskFlag> getRiskFlags() { return List.copyOf(riskFlags); }
    public String getClarificationMessage() { return clarificationMessage; }
    public OrderStatus getStatus() { return status; }
    public String getAgreedTerms() { return agreedTerms; }
    public String getPaypalInvoiceId() { return paypalInvoiceId; }
    public String getInvoiceUrl() { return invoiceUrl; }
    public String getPaymentId() { return paymentId; }
    public String getCarrier() { return carrier; }
    public String getTrackingNumber() { return trackingNumber; }
    public String getDisputeId() { return disputeId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getDisputedAt() { return disputedAt; }
}
