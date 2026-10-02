package com.tubadev.receivables.infrastructure.configuration;

import com.tubadev.receivables.infrastructure.jdbc.DatabaseClient;
import com.tubadev.receivables.infrastructure.jdbc.JdbcClientAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods = false)
public class JdbcConfig {

    @Bean
    DatabaseClient databaseClient(final JdbcClient jdbcClient) {
        return new JdbcClientAdapter(jdbcClient);
    }
}
