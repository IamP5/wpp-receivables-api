package com.tubadev.receivables.infrastructure.observer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Observer pattern: delivers an event to every interested subscriber. Returns {@code false} when any of
 * them failed, so the outbox keeps the event for a retry.
 */
public class Publisher<T> {

    private static final Logger LOG = LoggerFactory.getLogger(Publisher.class);

    private final List<Subscriber<T>> subscribers = new CopyOnWriteArrayList<>();

    public void register(final Subscriber<T> subscriber) {
        this.subscribers.add(subscriber);
    }

    public boolean publish(final T event) {
        var success = true;
        for (var sub : subscribers) {
            try {
                if (sub.test(event)) {
                    sub.onEvent(event);
                }
            } catch (final Exception ex) {
                LOG.error("Subscriber {} failed to handle {}", sub.getClass().getSimpleName(), event, ex);
                success = false;
            }
        }
        return success;
    }
}
