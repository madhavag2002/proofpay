package dev.proofpay.proofpay.ai;

import dev.proofpay.proofpay.order.RiskLevel;
import dev.proofpay.proofpay.order.RiskType;

import java.util.List;

/** Used when AI_MODE=mock or no key is set, so the app runs offline. Not real analysis. */
public class MockOrderAnalyzer implements OrderAnalyzer {

    @Override
    public OrderAnalysis analyze(ChatInput input) {
        String text = input.text() == null ? "(screenshots)" : input.text();
        String specs = text.length() > 200 ? text.substring(0, 200) + "..." : text;
        return new OrderAnalysis(
                null, "en",
                "Mock mode: this draft was not written by AI. Edit the item below.",
                List.of(new OrderAnalysis.Item("Item from chat", specs, null, null)),
                "USD", null, null, null, null,
                List.of(),
                List.of("Mock mode is on. Set AI_MODE=live and OPENAI_API_KEY to analyze chats."),
                List.of(new OrderAnalysis.Flag(RiskType.MISSING_PRICE, RiskLevel.HIGH,
                                "Mock flag: no agreed price", "INCORRECT_AMOUNT"),
                        new OrderAnalysis.Flag(RiskType.VAGUE_SPEC, RiskLevel.MEDIUM,
                                "Mock flag: size not stated", "MERCHANDISE_OR_SERVICE_NOT_AS_DESCRIBED")),
                "(Mock) Could you confirm the size and the price?");
    }

    @Override
    public boolean isMock() {
        return true;
    }
}
