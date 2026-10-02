package com.tubadev.receivables.domain.conversation;

import com.tubadev.receivables.domain.ValueObject;

import java.time.Duration;

/**
 * How long a session (one {@link Conversation}) lives without interaction. The WhatsApp chat is a single thread forever,
 * so sessions end on our side: by outcome (completed, declined...) or by inactivity.
 *
 * @param idleTimeout    bot stages: time without a customer message. Keep it close to the offer validity, so a customer
 *                       coming back finds a fresh menu instead of a stale offer.
 * @param handoffTimeout human handoff: time without anyone on our side (agent or bot) talking. Customer messages don't
 *                       count, otherwise a customer waiting for an agent who never comes would keep the bot silent forever.
 */
public record SessionPolicy(Duration idleTimeout, Duration handoffTimeout) implements ValueObject {

    public SessionPolicy {
        this.assertArgumentNotNull(idleTimeout, "'idleTimeout' should not be null");
        this.assertArgumentNotNull(handoffTimeout, "'handoffTimeout' should not be null");
        this.assertConditionTrue(idleTimeout.isPositive(), "'idleTimeout' should be positive");
        this.assertConditionTrue(handoffTimeout.isPositive(), "'handoffTimeout' should be positive");
    }
}
