package com.tubadev.receivables.infrastructure.gateway.repository;

import com.tubadev.receivables.domain.DomainEvent;
import com.tubadev.receivables.domain.utils.InstantUtils;
import com.tubadev.receivables.infrastructure.jdbc.DatabaseClient;
import com.tubadev.receivables.infrastructure.jdbc.JdbcUtils;
import com.tubadev.receivables.infrastructure.json.Json;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Transactional outbox: domain events are stored in the same transaction as the aggregate that raised them,
 * then relayed to subscribers by {@code OutboxRelayJob}.
 */
@Repository
public class EventJdbcRepository {

    private final DatabaseClient database;

    public EventJdbcRepository(final DatabaseClient databaseClient) {
        this.database = Objects.requireNonNull(databaseClient);
    }

    public record StoredEvent(Long eventId, int attempts, DomainEvent event) {}

    public void saveAll(final Collection<DomainEvent> events) {
        for (var ev : events) {
            final var sql = """
                    INSERT INTO events (processed, attempts, aggregate_id, aggregate_type, event_type, event_date, event_data)
                    VALUES (false, 0, :aggregateId, :aggregateType, :eventType, :eventDate, :eventData)
                    """;
            final var params = new HashMap<String, Object>();
            params.put("aggregateId", ev.aggregateId());
            params.put("aggregateType", ev.aggregateType());
            params.put("eventType", ev.getClass().getName());
            params.put("eventDate", JdbcUtils.toTimestamp(InstantUtils.now()));
            params.put("eventData", Json.writeValueAsString(ev));
            this.database.update(sql, params);
        }
    }

    public List<StoredEvent> unprocessedEvents(final int limit, final int maxAttempts) {
        final var sql = """
                SELECT event_id, attempts, event_type, event_data FROM events
                WHERE processed = false AND attempts < :maxAttempts
                ORDER BY event_id
                LIMIT :limit
                """;
        return this.database.query(sql, Map.of("limit", limit, "maxAttempts", maxAttempts), rs -> new StoredEvent(
                rs.getLong("event_id"),
                rs.getInt("attempts"),
                toDomainEvent(rs.getString("event_type"), rs.getString("event_data"))
        ));
    }

    public List<DomainEvent> eventsOfAggregate(final String aggregateId) {
        final var sql = "SELECT event_type, event_data FROM events WHERE aggregate_id = :aggregateId ORDER BY event_id";
        return this.database.query(sql, Map.of("aggregateId", aggregateId),
                rs -> toDomainEvent(rs.getString("event_type"), rs.getString("event_data")));
    }

    public void markAsProcessed(final Long eventId) {
        this.database.update("UPDATE events SET processed = true, attempts = attempts + 1 WHERE event_id = :id", Map.of("id", eventId));
    }

    public void markAsFailed(final Long eventId, final String error) {
        final var params = new HashMap<String, Object>();
        params.put("id", eventId);
        params.put("error", error == null ? null : error.substring(0, Math.min(error.length(), 1000)));
        this.database.update("UPDATE events SET attempts = attempts + 1, last_error = :error WHERE event_id = :id", params);
    }

    private static DomainEvent toDomainEvent(final String eventType, final String data) {
        try {
            final var type = Class.forName(eventType);
            if (!DomainEvent.class.isAssignableFrom(type)) {
                throw new IllegalStateException("%s is not a DomainEvent".formatted(eventType));
            }
            return (DomainEvent) Json.readValue(data, type);
        } catch (final ClassNotFoundException e) {
            throw new IllegalStateException("Unknown event type %s".formatted(eventType), e);
        }
    }
}
