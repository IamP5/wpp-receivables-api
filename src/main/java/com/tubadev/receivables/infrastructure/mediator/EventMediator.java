package com.tubadev.receivables.infrastructure.mediator;

import com.tubadev.receivables.domain.DomainEvent;
import com.tubadev.receivables.infrastructure.gateway.repository.EventJdbcRepository;
import com.tubadev.receivables.infrastructure.observer.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Bridges the outbox table and the in-process subscribers.
 */
@Component
public class EventMediator {

    private static final Logger LOG = LoggerFactory.getLogger(EventMediator.class);

    private final EventJdbcRepository eventRepository;
    private final Publisher<DomainEvent> domainEventPublisher;

    public EventMediator(final EventJdbcRepository eventRepository, final Publisher<DomainEvent> domainEventPublisher) {
        this.eventRepository = Objects.requireNonNull(eventRepository);
        this.domainEventPublisher = Objects.requireNonNull(domainEventPublisher);
    }

    /** @return how many events were relayed */
    public int relayPending(final int batchSize, final int maxAttempts) {
        final var events = this.eventRepository.unprocessedEvents(batchSize, maxAttempts);

        for (var stored : events) {
            if (this.domainEventPublisher.publish(stored.event())) {
                this.eventRepository.markAsProcessed(stored.eventId());
            } else {
                LOG.warn("Event {} failed [attempt:{}]", stored.eventId(), stored.attempts() + 1);
                this.eventRepository.markAsFailed(stored.eventId(), "subscriber failure");
            }
        }
        return events.size();
    }
}
