package org.springframework.cloud.gateway.sample;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import reactor.core.publisher.Mono;

@Configuration
@AutoConfigureBefore(GatewayAutoConfiguration.class)
public class Config {

    @Bean
    @Lazy(false)
    public KeyResolver userKeyResolver() {
        return exchange -> Mono.just(exchange.getRequest().getQueryParams().getFirst("user"));
    }

}
