package dev.proofpay.proofpay.dispute;

import java.util.List;

/** Offline stand-in when AI_MODE=mock. Not real analysis. */
public class MockDisputeAnalyzer implements DisputeAnalyzer {

    @Override
    public DisputeAnalysis analyze(DisputeCase d, Playbook playbook, EvidencePack evidence) {
        String ref = evidence.items().get(0).id();
        return new DisputeAnalysis(
                List.of(new DisputeAnalysis.ClaimPoint("Mock: the buyer's claim", "Mock: see the record", ref,
                        DisputeAnalysis.Support.PARTLY)),
                CaseStrength.MEDIUM, Recommendation.REPLY_WITH_UPDATE,
                List.of("Mock mode: set AI_MODE=live for a real assessment."),
                List.of("Mock: courier proof of delivery"),
                "Mock reply: the order was paid and shipped as agreed. Please see the attached details.");
    }

    @Override
    public boolean isMock() {
        return true;
    }
}
