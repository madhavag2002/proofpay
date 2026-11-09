package dev.proofpay.proofpay.dispute;

import dev.proofpay.proofpay.ai.AiException;
import dev.proofpay.proofpay.ai.OpenAiFunctionCaller;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;

public class OpenAiDisputeAnalyzer implements DisputeAnalyzer {

    static final String FUNCTION = "record_dispute_analysis";

    private final OpenAiFunctionCaller caller;
    private final JsonMapper mapper;
    private final Clock clock;
    private final String prompt;
    private final ObjectNode schema;

    public OpenAiDisputeAnalyzer(OpenAiFunctionCaller caller, JsonMapper mapper, Clock clock) {
        this.caller = caller;
        this.mapper = mapper;
        this.clock = clock;
        this.prompt = load("ai/dispute-prompt.txt");
        this.schema = (ObjectNode) mapper.readTree(load("ai/dispute-analysis-schema.json"));
    }

    @Override
    public DisputeAnalysis analyze(DisputeCase d, Playbook playbook, EvidencePack evidence) {
        String system = prompt
                .replace("{{TODAY}}", LocalDate.now(clock).toString())
                .replace("{{REASON}}", d.getReason())
                .replace("{{STAGE}}", String.valueOf(d.getStage()))
                .replace("{{AMOUNT}}", d.getAmount() + " " + d.getCurrency())
                .replace("{{PLAYBOOK}}", playbook.guidance())
                .replace("{{EVIDENCE}}", evidence.forPrompt());
        String user = "<dispute>\n" + d.getPaypalJson() + "\n</dispute>";
        JsonNode args = caller.call(system, user, FUNCTION, "Record the dispute assessment and draft reply.", schema);
        try {
            return mapper.treeToValue(args, DisputeAnalysis.class);
        } catch (JacksonException e) {
            throw new AiException("The AI's dispute assessment did not match the expected format. Try again.", e);
        }
    }

    @Override
    public boolean isMock() {
        return false;
    }

    private static String load(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing resource " + path, e);
        }
    }
}
