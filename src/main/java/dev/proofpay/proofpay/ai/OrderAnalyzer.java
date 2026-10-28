package dev.proofpay.proofpay.ai;

/** Reads a chat and returns the draft order plus dispute risks. */
public interface OrderAnalyzer {

    OrderAnalysis analyze(ChatInput input);

    boolean isMock();
}
