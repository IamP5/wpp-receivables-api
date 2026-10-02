package com.tubadev.receivables.infrastructure.job;

import com.tubadev.receivables.infrastructure.mediator.EventMediator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Polls the outbox. Single instance only: for horizontal scaling use {@code FOR UPDATE SKIP LOCKED}
 * or CDC (Debezium → Kafka) like the reference architecture.
 */
@Component
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayJob {

    private final EventMediator eventMediator;
    private final int batchSize;
    private final int maxAttempts;

    public OutboxRelayJob(
            final EventMediator eventMediator,
            @Value("${outbox.relay.batch-size:50}") final int batchSize,
            @Value("${outbox.relay.max-attempts:5}") final int maxAttempts
    ) {
        this.eventMediator = Objects.requireNonNull(eventMediator);
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay:2s}")
    public void relay() {
        this.eventMediator.relayPending(batchSize, maxAttempts);
    }
}
