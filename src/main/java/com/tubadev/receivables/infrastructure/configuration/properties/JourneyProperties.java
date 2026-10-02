package com.tubadev.receivables.infrastructure.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Session lifecycle of the WhatsApp journey (see {@code SessionPolicy}).
 *
 * @param sessionTimeout closes a bot session after this long without a customer message; matches the offer validity
 * @param handoffTimeout gives the customer back to the bot after this long without an agent message
 */
@ConfigurationProperties(prefix = "journey")
public record JourneyProperties(
        @DefaultValue("30m") Duration sessionTimeout,
        @DefaultValue("2h") Duration handoffTimeout
) {
}
