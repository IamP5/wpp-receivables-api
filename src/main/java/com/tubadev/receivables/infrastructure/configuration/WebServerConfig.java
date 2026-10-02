package com.tubadev.receivables.infrastructure.configuration;

import com.tubadev.receivables.domain.DomainEvent;
import com.tubadev.receivables.infrastructure.json.Json;
import com.tubadev.receivables.infrastructure.observer.Publisher;
import com.tubadev.receivables.infrastructure.observer.Subscriber;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@ConfigurationPropertiesScan("com.tubadev.receivables.infrastructure.configuration.properties")
@EnableScheduling
public class WebServerConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    JsonMapper jsonMapper() {
        return Json.mapper();
    }

    @Bean
    Publisher<DomainEvent> domainEventPublisher(final List<Subscriber<DomainEvent>> subscribers) {
        final var publisher = new Publisher<DomainEvent>();
        subscribers.forEach(publisher::register);
        return publisher;
    }
}
