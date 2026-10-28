package dev.proofpay.proofpay.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.proofpay.proofpay.order.RiskLevel;
import dev.proofpay.proofpay.order.RiskType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** What the AI read from the chat. Every value is a suggestion the seller checks before invoicing. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderAnalysis(
        @JsonProperty("transcript") String transcript,
        @JsonProperty("buyer_language") String buyerLanguage,
        @JsonProperty("english_summary") String englishSummary,
        @JsonProperty("items") List<Item> items,
        @JsonProperty("currency") String currency,
        @JsonProperty("ship_to") String shipTo,
        @JsonProperty("ship_to_country") String shipToCountry,
        @JsonProperty("delivery_by") String deliveryBy,
        @JsonProperty("delivery_deadline") String deliveryDeadline,
        @JsonProperty("buyer_accepted_notes") List<String> buyerAcceptedNotes,
        @JsonProperty("open_questions") List<String> openQuestions,
        @JsonProperty("risk_flags") List<Flag> riskFlags,
        @JsonProperty("clarification_message") String clarificationMessage) {

    public OrderAnalysis {
        items = clean(items);
        buyerAcceptedNotes = clean(buyerAcceptedNotes);
        openQuestions = clean(openQuestions);
        riskFlags = clean(riskFlags);
    }

    private static <T> List<T> clean(List<T> list) {
        return list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            @JsonProperty("name") String name,
            @JsonProperty("specs") String specs,
            @JsonProperty("quantity") Integer quantity,
            @JsonProperty("unit_price") BigDecimal unitPrice) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Flag(
            @JsonProperty("type") RiskType type,
            @JsonProperty("level") RiskLevel level,
            @JsonProperty("detail") String detail,
            @JsonProperty("prevents") String prevents) {
    }
}
