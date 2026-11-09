package dev.proofpay.proofpay.dispute;

import dev.proofpay.proofpay.ai.OpenAiFunctionCaller;
import dev.proofpay.proofpay.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

@Configuration
class DisputeAiConfig {

    @Bean
    DisputeAnalyzer disputeAnalyzer(AppProperties props, JsonMapper mapper, Clock clock) {
        if (props.ai().isMock()) {
            return new MockDisputeAnalyzer();
        }
        return new OpenAiDisputeAnalyzer(new OpenAiFunctionCaller(props, mapper), mapper, clock);
    }
}
