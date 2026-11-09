package dev.proofpay.proofpay.dispute;

import dev.proofpay.proofpay.dispute.DisputeAnalysis.ClaimPoint;
import dev.proofpay.proofpay.dispute.DisputeAnalysis.Support;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Rules the AI cannot override: citations must exist, and "not received" needs tracking to be fought. */
final class DisputeGuard {

    private DisputeGuard() {
    }

    static DisputeAnalysis apply(DisputeAnalysis a, EvidencePack pack, Playbook playbook, CourierStatus courier) {
        Set<String> ids = pack.ids();
        List<ClaimPoint> points = a.claimPoints().stream().map(p -> {
            if (p.evidenceRef() != null && ids.contains(p.evidenceRef())) {
                return p;
            }
            // A claim of support without a real record is not support.
            Support s = p.supportsSeller() == Support.YES ? Support.PARTLY : p.supportsSeller();
            return new ClaimPoint(p.buyerSays(), p.evidence(), "NONE", s);
        }).toList();

        CaseStrength strength = a.strength() == null ? CaseStrength.WEAK : a.strength();
        Recommendation rec = a.recommendation() == null ? Recommendation.REPLY_WITH_UPDATE : a.recommendation();
        List<String> reasons = new ArrayList<>(a.reasons());

        if (playbook == Playbook.NOT_RECEIVED) {
            if (!pack.hasTracking()) {
                if (strength != CaseStrength.WEAK) {
                    strength = CaseStrength.WEAK;
                    reasons.add("ProofPay rule: with no tracking on file, the case cannot be rated stronger than weak.");
                }
                if (rec == Recommendation.FIGHT) {
                    rec = Recommendation.REPLY_WITH_UPDATE;
                    reasons.add("ProofPay rule: fighting a 'not received' claim without tracking is unlikely to succeed.");
                }
            } else if (courier == CourierStatus.DELIVERED && strength == CaseStrength.WEAK) {
                strength = CaseStrength.MEDIUM;
                reasons.add("ProofPay rule: tracking marked delivered makes the case at least medium.");
            }
        }
        String draft = a.draftReply() == null ? "" : a.draftReply().strip();
        return new DisputeAnalysis(points, strength, rec, reasons, a.missingEvidence(), draft);
    }
}
