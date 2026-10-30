package dev.proofpay.proofpay.agent;

import dev.proofpay.proofpay.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
class AgentConfig {

    @Bean
    AgentModel agentModel(AppProperties props, JsonMapper mapper) {
        return new OpenAiAgentModel(props, mapper);
    }
}
