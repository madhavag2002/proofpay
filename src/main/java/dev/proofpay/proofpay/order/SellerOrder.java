package dev.proofpay.proofpay.order;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** An order taken over chat: the buyer's words, the approved terms, and the PayPal records. */
@Entity
public class SellerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Optimistic lock: two clicks at once cannot both change the order. */
    @Version
    private long version;

    @Column(nullable = false)
    private String buyerName;

    @Column(nullable = false)
    private String buyerEmail;

    @Column(nullable = false, length = 8000)
    private String buyerMessage;

    private String buyerLanguage;

    @Column(length = 2000)
    private String englishSummary;

    @Column(nullable = false, length = 3)
    private String currency;

    private String shipTo;
    private String deliveryBy;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_item", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "sort_index")
    private List<OrderItem> items = new ArrayList<>();

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

    private String trackingNumber;
    private String carrier;

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
        this.buyerMessage = buyerMessage;
        this.currency = currency;
    }

    // ---- State changes. Each checks the current status, so steps can't be skipped or repeated.

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

    // ---- Getters (no setters on purpose)

    public Long getId() { return id; }
    public String getBuyerName() { return buyerName; }
    public String getBuyerEmail() { return buyerEmail; }
    public String getBuyerMessage() { return buyerMessage; }
    public String getBuyerLanguage() { return buyerLanguage; }
    public String getEnglishSummary() { return englishSummary; }
    public String getCurrency() { return currency; }
    public String getShipTo() { return shipTo; }
    public String getDeliveryBy() { return deliveryBy; }
    public List<OrderItem> getItems() { return List.copyOf(items); }
    public List<String> getAcceptedNotes() { return List.copyOf(acceptedNotes); }
    public List<String> getOpenQuestions() { return List.copyOf(openQuestions); }
    public OrderStatus getStatus() { return status; }
    public String getAgreedTerms() { return agreedTerms; }
    public String getPaypalInvoiceId() { return paypalInvoiceId; }
    public String getInvoiceUrl() { return invoiceUrl; }
    public String getPaymentId() { return paymentId; }
    public String getTrackingNumber() { return trackingNumber; }
    public String getCarrier() { return carrier; }
    public String getDisputeId() { return disputeId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getDisputedAt() { return disputedAt; }
}
