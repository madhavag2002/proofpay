package dev.proofpay.proofpay.ai;

import dev.proofpay.proofpay.config.AppProperties;
import dev.proofpay.proofpay.seller.SellerProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** Picks the real or mock analyzer from configuration. */
@Configuration
class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    OrderAnalyzer orderAnalyzer(AppProperties props, JsonMapper mapper, SellerProfileService profiles) {
        if (props.ai().isMock()) {
            log.warn("AI mock mode: set AI_MODE=live and OPENAI_API_KEY to analyze chats with OpenAI");
            return new MockOrderAnalyzer();
        }
        log.info("AI live mode with model {}", props.ai().model());
        return new OpenAiOrderAnalyzer(props, mapper, profiles);
    }
}
