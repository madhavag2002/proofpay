package dev.proofpay.proofpay.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** One clock in the seller's time zone, so dates on invoices match the seller's calendar. */
@Configuration
class TimeConfig {

    @Bean
    Clock clock(AppProperties props) {
        return Clock.system(ZoneId.of(props.zoneOrDefault()));
    }
}
