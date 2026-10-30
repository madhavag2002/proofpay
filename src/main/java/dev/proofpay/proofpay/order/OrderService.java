package dev.proofpay.proofpay.order;

import dev.proofpay.proofpay.ai.ChatInput;
import dev.proofpay.proofpay.ai.OrderAnalysis;
import dev.proofpay.proofpay.ai.OrderAnalyzer;
import dev.proofpay.proofpay.paypal.InvoiceGateway;
import dev.proofpay.proofpay.paypal.InvoiceGateway.SentInvoice;
import dev.proofpay.proofpay.paypal.PayPalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The order workflow: chat -> AI draft -> seller edits and confirms -> PayPal invoice -> paid.
 * AI and PayPal calls run outside database transactions, so a slow network never holds a connection.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final OrderAnalyzer analyzer;
    private final InvoiceGateway invoices;
    private final TransactionTemplate tx;
    private final Clock clock;

    /** Orders being sent right now; stops a double click from sending two invoices. */
    private final Set<Long> sending = ConcurrentHashMap.newKeySet();

    public OrderService(OrderRepository orders, OrderAnalyzer analyzer, InvoiceGateway invoices,
                        TransactionTemplate tx, Clock clock) {
        this.orders = orders;
        this.analyzer = analyzer;
        this.invoices = invoices;
        this.tx = tx;
        this.clock = clock;
    }

    // ---- 1. Chat in, draft out

    /** The AI reads the chat; a DRAFT is saved with gaps left empty for the seller. */
    public SellerOrder createFromChat(String buyerName, String buyerEmail, String currency, ChatInput chat) {
        String chosenCurrency = normalizeCurrency(currency);
        OrderAnalysis a = analyzer.analyze(chat); // outside any transaction; may throw AiException

        SellerOrder order = new SellerOrder(buyerName.strip(), buyerEmail.strip(), chatText(chat, a), chosenCurrency);

        List<OrderItem> items = a.items().stream()
                .map(i -> new OrderItem(i.name(), i.specs(), i.quantity(), i.unitPrice()))
                .toList();

        List<String> questions = new ArrayList<>(a.openQuestions());
        if (a.currency() != null && !a.currency().equals(chosenCurrency)) {
            questions.add("The chat mentions " + a.currency() + ", but this invoice is in " + chosenCurrency
                    + ". Check the currency and prices.");
        }
        if (items.isEmpty()) {
            questions.add("The AI found no items. Add them by hand.");
        }

        order.recordAiDraft(a.buyerLanguage(), a.englishSummary(), items,
                clip(a.shipTo(), 255), clip(a.deliveryBy(), 255),
                clipAll(a.buyerAcceptedNotes()), clipAll(questions));
        order.recordRiskCheck(chat.source(),
                a.riskFlags().stream().map(f -> new RiskFlag(f.type(), f.level(), f.detail(), f.prevents())).toList(),
                a.clarificationMessage());

        SellerOrder saved = tx.execute(status -> orders.save(order));
        log.info("Order {} drafted from {} chat: {} items, {} risk flags",
                saved.getId(), chat.source(), items.size(), saved.getRiskFlags().size());
        return saved;
    }

    // ---- 2. Seller edits

    /** Saves edits even if incomplete, so the seller can wait for the buyer's answer. */
    public ApprovalCheck saveDraft(long orderId, DraftEdit edit) {
        return tx.execute(status -> {
            SellerOrder order = load(orderId);
            order.updateDraft(edit.items(), edit.shipTo(), edit.deliveryBy(), edit.acceptedNotes());
            orders.save(order);
            return ApprovalCheck.of(order);
        });
    }

    public void resolveFlag(long orderId, int flagIndex) {
        tx.executeWithoutResult(status -> {
            SellerOrder order = load(orderId);
            order.resolveFlag(flagIndex);
            orders.save(order);
        });
    }

    // ---- 3. Approve and send

    /**
     * Saves the edits, checks them, and only then creates and sends the PayPal invoice.
     *
     * @param edit        the seller's latest edits, or null to send the draft as it is
     * @param sendAnyway  the seller confirmed sending while a HIGH risk is still open
     */
    public SendResult approveAndSend(long orderId, DraftEdit edit, boolean sendAnyway) {
        if (!sending.add(orderId)) {
            return SendResult.busy();
        }
        try {
            SellerOrder edited = tx.execute(status -> {
                SellerOrder order = load(orderId);
                if (order.getStatus() != OrderStatus.DRAFT) {
                    return null;
                }
                if (edit != null) {
                    order.updateDraft(edit.items(), edit.shipTo(), edit.deliveryBy(), edit.acceptedNotes());
                }
                return orders.saveAndFlush(order);
            });
            if (edited == null) {
                return SendResult.alreadySent();
            }

            ApprovalCheck check = ApprovalCheck.of(edited);
            if (!check.canSend()) {
                return SendResult.missingFields(check);
            }
            if (check.openHighRisk() && !sendAnyway) {
                return SendResult.needsConfirmation(check);
            }

            String terms = AgreedTerms.render(edited, LocalDate.now(clock));
            SentInvoice sent;
            try {
                sent = invoices.createAndSend(edited, terms); // outside any transaction
            } catch (PayPalException e) {
                log.warn("PayPal refused the invoice for order {}: {}", orderId, e.getMessage());
                return SendResult.paypalError(check, e.getMessage());
            }

            tx.executeWithoutResult(status -> {
                SellerOrder order = load(orderId);
                order.markInvoiceSent(terms, sent.invoiceId(), sent.invoiceUrl(), check.openHighRisk());
                orders.save(order);
            });
            log.info("Order {} invoiced as {}{}", orderId, sent.invoiceId(),
                    check.openHighRisk() ? " (sent despite an open high risk)" : "");
            return SendResult.sent(check);
        } finally {
            sending.remove(orderId);
        }
    }

    // ---- 4. Paid, and the not-received alert

    /** PayPal says the invoice was paid. Safe to call repeatedly; PayPal retries webhooks. */
    public void markPaidByInvoice(String invoiceId, String paymentId) {
        tx.executeWithoutResult(status -> orders.findByPaypalInvoiceId(invoiceId).ifPresentOrElse(order -> {
            if (order.getStatus() == OrderStatus.DRAFT) {
                log.warn("Paid event for invoice {} but order {} is still a draft; ignoring", invoiceId, order.getId());
                return;
            }
            if (order.markPaid(paymentId)) {
                orders.save(order);
                log.info("Order {} paid (invoice {})", order.getId(), invoiceId);
            }
        }, () -> log.warn("Paid event for unknown invoice {}", invoiceId)));
    }

    /** Paid more than `days` ago with no tracking: a "not received" dispute waiting to happen. */
    public List<SellerOrder> paidWithoutTracking(int days) {
        return orders.findByStatusAndTrackingNumberIsNullAndPaidAtBefore(
                OrderStatus.PAID, clock.instant().minus(Duration.ofDays(days)));
    }

    /** Saves tracking added through the assistant on the order paid with this PayPal transaction. */
    public Optional<Long> recordTrackingByPayment(String paymentId, String carrier, String trackingNumber) {
        return tx.execute(status -> orders.findByPaymentId(paymentId)
                .filter(o -> o.getStatus() == OrderStatus.PAID)
                .map(o -> {
                    o.recordTracking(carrier, trackingNumber);
                    orders.save(o);
                    log.info("Order {} tracking saved: {} {}", o.getId(), carrier, trackingNumber);
                    return o.getId();
                }));
    }

    // ---- Reads

    public SellerOrder load(long id) {
        return orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    public List<SellerOrder> listAll() {
        return orders.findAllByOrderByCreatedAtDesc();
    }

    // ---- Helpers

    private static String chatText(ChatInput chat, OrderAnalysis a) {
        if (chat.text() != null && !chat.text().isBlank()) {
            return chat.text();
        }
        if (a.transcript() != null && !a.transcript().isBlank()) {
            return a.transcript();
        }
        return "(" + chat.images().size() + " screenshot(s); the AI could not read a transcript)";
    }

    private static String normalizeCurrency(String currency) {
        String c = currency == null ? "" : currency.strip().toUpperCase(Locale.ROOT);
        if (!c.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Choose a currency, e.g. USD");
        }
        return c;
    }

    private static String clip(String s, int max) {
        return s == null || s.isBlank() ? null : OrderItem.clip(s.strip(), max);
    }

    private static List<String> clipAll(List<String> list) {
        return list.stream().filter(s -> s != null && !s.isBlank()).map(s -> OrderItem.clip(s.strip(), 500)).toList();
    }
}
