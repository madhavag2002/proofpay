package dev.proofpay.proofpay.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Analyzes the sample chats and prints the results. Runs only with the "aispike" profile. */
@Component
@Profile("aispike")
class AiSpike implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AiSpike.class);

    private final OrderAnalyzer analyzer;
    private final JsonMapper mapper;
    private final String screenshotPath;

    AiSpike(OrderAnalyzer analyzer, JsonMapper mapper, @Value("${SPIKE_SCREENSHOT:}") String screenshotPath) {
        this.analyzer = analyzer;
        this.mapper = mapper;
        this.screenshotPath = screenshotPath;
    }

    @Override
    public void run(String... args) throws IOException {
        runOne("clear (pasted)", ChatInput.pasted(sample("clear.txt")));
        runOne("vague (pasted)", ChatInput.pasted(sample("vague.txt")));
        runOne("WhatsApp export", ChatInput.whatsappExport(sample("whatsapp-export.txt")));

        if (!screenshotPath.isBlank()) {
            Path path = Path.of(screenshotPath);
            ChatImage image = new ChatImage(ChatImage.mediaTypeFor(path.toString()), Files.readAllBytes(path));
            runOne("screenshot " + path.getFileName(), ChatInput.screenshots(List.of(image)));
        }
    }

    private void runOne(String name, ChatInput input) {
        try {
            OrderAnalysis result = analyzer.analyze(input);
            log.info("=== {} ===\n{}", name, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        } catch (AiException | IllegalArgumentException e) {
            log.error("=== {} === failed: {}", name, e.getMessage());
        }
    }

    private static String sample(String file) throws IOException {
        return new ClassPathResource("samples/" + file).getContentAsString(StandardCharsets.UTF_8);
    }
}
