package dev.proofpay.proofpay.dispute;

public interface DisputeAnalyzer {

    DisputeAnalysis analyze(DisputeCase dispute, Playbook playbook, EvidencePack evidence);

    boolean isMock();
}
