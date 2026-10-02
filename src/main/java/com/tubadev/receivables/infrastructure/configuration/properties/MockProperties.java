package com.tubadev.receivables.infrastructure.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Seed data of the mock adapters that stand in for the Customer and Receivables services.
 */
@ConfigurationProperties(prefix = "mock")
public record MockProperties(
        @DefaultValue List<MockCustomer> customers,
        @DefaultValue Anticipation anticipation,
        @DefaultValue Biometrics biometrics
) {

    public record MockCustomer(
            String id,
            String name,
            String document,
            String documentType,
            String phoneNumber,
            @DefaultValue("true") boolean optedIn,
            @DefaultValue List<MockReceivable> receivables
    ) {}

    public record MockReceivable(String payerName, BigDecimal amount, int dueInDays) {}

    public record Anticipation(
            @DefaultValue("0.0199") BigDecimal monthlyRate,
            @DefaultValue("500.00") BigDecimal minimumAmount,
            @DefaultValue("30m") Duration offerTtl
    ) {}

    public record Biometrics(
            @DefaultValue("10240") int minImageBytes,
            @DefaultValue("0") int failFirstAttempts,
            @DefaultValue("0.97") BigDecimal approvalScore
    ) {}
}
