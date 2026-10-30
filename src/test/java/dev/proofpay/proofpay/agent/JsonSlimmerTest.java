package dev.proofpay.proofpay.agent;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSlimmerTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void removesLinksEverywhere() {
        String slim = JsonSlimmer.slim(mapper,
                "{\"items\":[{\"id\":\"INV2-1\",\"links\":[{\"href\":\"x\"}]}],\"links\":[{\"href\":\"y\"}]}", 1000);
        assertThat(slim).isEqualTo("{\"items\":[{\"id\":\"INV2-1\"}]}");
    }

    @Test
    void cutsLongTextAndKeepsNonJson() {
        assertThat(JsonSlimmer.slim(mapper, "plain text", 100)).isEqualTo("plain text");
        assertThat(JsonSlimmer.slim(mapper, "x".repeat(50), 10)).isEqualTo("xxxxxxxxxx...(truncated)");
    }

    @Test
    void findsValuesInNestedArguments() {
        ObjectNode args = (ObjectNode) mapper.readTree(
                "{\"trackers\":[{\"transaction_id\":\"9X1\",\"tracking_number\":\"D123\",\"carrier\":\"OTHER\",\"carrier_name_other\":\"DTDC\"}]}");
        assertThat(JsonSlimmer.findText(args, "transaction_id")).isEqualTo("9X1");
        assertThat(JsonSlimmer.findText(args, "tracking_number")).isEqualTo("D123");
        assertThat(JsonSlimmer.findText(args, "carrier")).isEqualTo("OTHER");  // exact key wins
        assertThat(JsonSlimmer.findText(args, "missing")).isNull();
    }
}
