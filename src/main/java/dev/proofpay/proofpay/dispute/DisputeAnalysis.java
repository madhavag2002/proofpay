package dev.proofpay.proofpay.dispute;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/** The AI's assessment, after DisputeGuard has checked it. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DisputeAnalysis(
        @JsonProperty("claim_points") List<ClaimPoint> claimPoints,
        @JsonProperty("strength") CaseStrength strength,
        @JsonProperty("recommendation") Recommendation recommendation,
        @JsonProperty("reasons") List<String> reasons,
        @JsonProperty("missing_evidence") List<String> missingEvidence,
        @JsonProperty("draft_reply") String draftReply) {

    public DisputeAnalysis {
        claimPoints = clean(claimPoints);
        reasons = clean(reasons);
        missingEvidence = clean(missingEvidence);
    }

    private static <T> List<T> clean(List<T> list) {
        return list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }

    public enum Support { YES, PARTLY, NO }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClaimPoint(
            @JsonProperty("buyer_says") String buyerSays,
            @JsonProperty("evidence") String evidence,
            @JsonProperty("evidence_ref") String evidenceRef,
            @JsonProperty("supports_seller") Support supportsSeller) {
    }
}
