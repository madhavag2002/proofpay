package dev.proofpay.proofpay.dispute;

import dev.proofpay.proofpay.agent.JsonSlimmer;
import dev.proofpay.proofpay.config.AppProperties;
import dev.proofpay.proofpay.order.OrderService;
import dev.proofpay.proofpay.order.SellerOrder;
import dev.proofpay.proofpay.paypal.PayPalClient;
import dev.proofpay.proofpay.paypal.PayPalException;
import dev.proofpay.proofpay.seller.SellerProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Disputes: import from PayPal, link to orders, analyze, and send the seller's response. */
@Service
public class DisputeService {

    private static final Logger log = LoggerFactory.getLogger(DisputeService.class);

    static final int MAX_NOTES = 2000;

    private final DisputeCaseRepository repo;
    private final OrderService orders;
    private final PayPalClient paypal;
    private final DisputeAnalyzer analyzer;
    private final SellerProfileService profiles;
    private final JsonMapper mapper;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final AppProperties props;

    public DisputeService(DisputeCaseRepository repo, OrderService orders, PayPalClient paypal,
                          DisputeAnalyzer analyzer, SellerProfileService profiles, JsonMapper mapper,
                          TransactionTemplate tx, Clock clock, AppProperties props) {
        this.repo = repo;
        this.orders = orders;
        this.paypal = paypal;
        this.analyzer = analyzer;
        this.profiles = profiles;
        this.mapper = mapper;
        this.tx = tx;
        this.clock = clock;
        this.props = props;
    }

    // ---- From PayPal

    /** Creates or updates a case from PayPal's dispute JSON (a webhook resource or the dispute API). */
    public DisputeCase upsertFromPayPal(JsonNode dispute) {
        String disputeId = dispute.path("dispute_id").asString("");
        if (disputeId.isBlank()) {
            throw new IllegalArgumentException("The dispute has no dispute_id");
        }
        JsonNode d = dispute;
        if ((!d.has("reason") || !d.has("disputed_transactions")) && !props.paypal().isMock()) {
            d = paypal.getDispute(disputeId); // the webhook body may be a summary
        }
        JsonNode t = d.path("disputed_transactions").path(0);
        String invoiceId = textOrNull(t.path("invoice_number"));
        String sellerTx = textOrNull(t.path("seller_transaction_id"));
        Optional<SellerOrder> order = orders.findForDispute(invoiceId, sellerTx);

        List<String> allowed = new ArrayList<>(d.path("allowed_response_options").propertyNames());
        String claim = buyerClaim(d);
        String json = JsonSlimmer.slim(mapper, d.toString(), 19000);
        JsonNode finalD = d;

        DisputeCase saved = tx.execute(s -> {
            DisputeCase c = repo.findByDisputeId(disputeId).orElseGet(() -> new DisputeCase(disputeId));
            c.updateFromPayPal(
                    textOrNull(finalD.path("reason")),
                    textOrNull(finalD.path("dispute_life_cycle_stage")),
                    textOrNull(finalD.path("status")),
                    textOrNull(finalD.path("seller_response_due_date")),
                    textOrNull(finalD.path("dispute_amount").path("value")),
                    textOrNull(finalD.path("dispute_amount").path("currency_code")),
                    sellerTx, invoiceId, String.join(", ", allowed),
                    t.path("seller_protection_eligible").asBoolean(false),
                    claim, json);
            order.ifPresent(o -> c.linkOrder(o.getId()));
            return repo.save(c);
        });
        order.ifPresent(o -> orders.markDisputed(o.getId(), disputeId));
        log.info("Dispute {} ({}, {}) recorded{}", disputeId, saved.getReason(), saved.getStatus(),
                order.map(o -> " for order " + o.getId()).orElse(" with no matching order"));
        return saved;
    }

    /** Imports every dispute PayPal lists. Catches disputes whose webhook was missed. */
    public int syncAll() {
        requireLive();
        int count = 0;
        for (JsonNode item : paypal.listDisputes().path("items")) {
            String id = item.path("dispute_id").asString("");
            if (!id.isBlank()) {
                upsertFromPayPal(paypal.getDispute(id));
                count++;
            }
        }
        return count;
    }

    public DisputeCase refresh(long caseId) {
        requireLive();
        return upsertFromPayPal(paypal.getDispute(load(caseId).getDisputeId()));
    }

    // ---- Seller actions

    public void setCourierStatus(long caseId, CourierStatus status) {
        tx.executeWithoutResult(s -> {
            DisputeCase c = load(caseId);
            c.setCourierStatus(status);
            repo.save(c);
        });
    }

    public DisputeAnalysis analyze(long caseId) {
        DisputeCase c = load(caseId);
        Playbook playbook = Playbook.forReason(c.getReason());
        EvidencePack pack = evidenceFor(c);
        DisputeAnalysis raw = analyzer.analyze(c, playbook, pack); // outside any transaction
        DisputeAnalysis checked = DisputeGuard.apply(raw, pack, playbook, c.getCourierStatus());
        String json = mapper.writeValueAsString(checked);
        String draft = clip(checked.draftReply(), 4000);
        tx.executeWithoutResult(s -> {
            DisputeCase fresh = load(caseId);
            fresh.recordAnalysis(checked.strength(), checked.recommendation(), json, draft);
            repo.save(fresh);
        });
        log.info("Dispute {} analyzed: {} / {}", c.getDisputeId(), checked.strength(), checked.recommendation());
        return checked;
    }

    public void updateDraft(long caseId, String text) {
        tx.executeWithoutResult(s -> {
            DisputeCase c = load(caseId);
            c.updateDraft(clip(text == null ? "" : text.strip(), 4000));
            repo.save(c);
        });
    }

    /** Sends the seller's response to PayPal. Never accepts or refunds. */
    public void submit(long caseId) {
        requireLive();
        DisputeCase c = load(caseId);
        if (!c.canSubmit()) {
            throw new IllegalStateException("PayPal is not waiting for your response on this dispute right now.");
        }
        SellerOrder order = c.getOrderId() == null ? null : orders.load(c.getOrderId());
        boolean hasTracking = order != null && order.getTrackingNumber() != null;
        Playbook playbook = Playbook.forReason(c.getReason());

        ObjectNode input = mapper.createObjectNode();
        ObjectNode evidence = input.putArray("evidences").addObject();
        String type = playbook.evidenceTypeFor(hasTracking);
        evidence.put("evidence_type", type);
        if ("PROOF_OF_FULFILLMENT".equals(type)) {
            ObjectNode t = evidence.putObject("evidence_info").putArray("tracking_info").addObject();
            t.put("carrier_name", "OTHER");
            t.put("carrier_name_other", order.getCarrier() == null ? "Courier" : order.getCarrier());
            t.put("tracking_number", order.getTrackingNumber());
        }
        evidence.put("notes", clip(c.getDraftReply(), MAX_NOTES));

        try {
            paypal.provideEvidence(c.getDisputeId(), input);
        } catch (PayPalException e) {
            tx.executeWithoutResult(s -> {
                DisputeCase fresh = load(caseId);
                fresh.recordSubmitError(e.getMessage());
                repo.save(fresh);
            });
            throw e;
        }
        tx.executeWithoutResult(s -> {
            DisputeCase fresh = load(caseId);
            fresh.markSubmitted();
            repo.save(fresh);
        });
        log.info("Evidence sent for dispute {} ({})", c.getDisputeId(), type);
        try {
            refresh(caseId);
        } catch (PayPalException e) {
            log.warn("Could not refresh dispute {} after submitting: {}", c.getDisputeId(), e.getMessage());
        }
    }

    // ---- Reads

    public DisputeCase load(long caseId) {
        return repo.findById(caseId).orElseThrow(() -> new IllegalArgumentException("No dispute " + caseId));
    }

    public Optional<DisputeCase> findByPayPalId(String disputeId) {
        return repo.findByDisputeId(disputeId);
    }

    public List<DisputeCase> listAll() {
        return repo.findAllByOrderByCreatedAtDesc();
    }

    public EvidencePack evidenceFor(DisputeCase c) {
        SellerOrder order = c.getOrderId() == null ? null : orders.load(c.getOrderId());
        return EvidencePack.build(order, profiles.current(), c.getCourierStatus(), clock);
    }

    public DisputeAnalysis analysisOf(DisputeCase c) {
        if (c.getAnalysisJson() == null) {
            return null;
        }
        try {
            return mapper.readValue(c.getAnalysisJson(), DisputeAnalysis.class);
        } catch (JacksonException e) {
            return null;
        }
    }

    // ---- Helpers

    private void requireLive() {
        if (props.paypal().isMock()) {
            throw new IllegalStateException("Disputes need PAYPAL_MODE=live and the PayPal sandbox.");
        }
    }

    private static String buyerClaim(JsonNode d) {
        List<String> parts = new ArrayList<>();
        for (JsonNode m : d.path("messages")) {
            if ("BUYER".equals(m.path("posted_by").asString(""))) {
                parts.add(m.path("content").asString(""));
            }
        }
        String claim = parts.isEmpty()
                ? "No message from the buyer. Reason: " + d.path("reason").asString("unknown") + "."
                : String.join("\n", parts);
        return clip(claim, 2000);
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asString(null);
    }

    private static String clip(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
