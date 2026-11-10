package dev.proofpay.proofpay.dispute;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DisputeEvidenceTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void readsTheLatestRequestFromTheRealSandboxShape() {
        // Shaped like PP-R-XYA-10197416's evidences list.
        var dispute = mapper.readTree("""
                {"evidences":[
                  {"evidence_type":"CREATE","source":"SUBMITTED_BY_BUYER","notes":"i had not received the item",
                   "date":"2026-11-09T07:40:25.819Z"},
                  {"evidence_type":"OTHER","source":"REQUESTED_FROM_SELLER","date":"2026-11-09T08:00:00.000Z"},
                  {"evidence_type":"PROOF_OF_FULFILLMENT","source":"REQUESTED_FROM_SELLER","date":"2026-11-10T07:07:32.197Z"},
                  {"evidence_type":"PROOF_OF_REFUND","source":"REQUESTED_FROM_SELLER","date":"2026-11-10T07:07:32.197Z"},
                  {"evidence_type":"PROOF_OF_DELIVERY_SIGNATURE","source":"REQUESTED_FROM_SELLER","date":"2026-11-10T07:07:32.197Z"}
                ]}""");
        assertThat(DisputeService.requestedFromSeller(dispute))
                .containsExactly("PROOF_OF_FULFILLMENT", "PROOF_OF_REFUND", "PROOF_OF_DELIVERY_SIGNATURE");
    }

    @Test
    void neverSendsRefundProofAndTriesTheBestTypeFirst() {
        List<String> requested = List.of("PROOF_OF_REFUND", "PROOF_OF_DELIVERY_SIGNATURE", "PROOF_OF_FULFILLMENT");
        assertThat(DisputeService.evidenceCandidates(requested, Playbook.NOT_RECEIVED, true, true))
                .containsExactly("PROOF_OF_FULFILLMENT", "PROOF_OF_DELIVERY_SIGNATURE");
    }

    @Test
    void documentTypesNeedAFile() {
        List<String> requested = List.of("PROOF_OF_FULFILLMENT", "PROOF_OF_DELIVERY_SIGNATURE");
        assertThat(DisputeService.evidenceCandidates(requested, Playbook.NOT_RECEIVED, true, false))
                .containsExactly("PROOF_OF_FULFILLMENT");
        assertThat(DisputeService.evidenceCandidates(requested, Playbook.NOT_RECEIVED, false, false)).isEmpty();
        assertThat(DisputeService.evidenceCandidates(List.of("PROOF_OF_REFUND"), Playbook.NOT_RECEIVED, true, true)).isEmpty();
    }

    @Test
    void fallsBackToThePlaybookWhenPayPalAsksForNothing() {
        assertThat(DisputeService.evidenceCandidates(List.of(), Playbook.NOT_RECEIVED, true, false))
                .containsExactly("PROOF_OF_FULFILLMENT");
        assertThat(DisputeService.evidenceCandidates(List.of(), Playbook.NOT_RECEIVED, false, false))
                .containsExactly("OTHER");
    }
}
