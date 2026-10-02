package com.tubadev.receivables.infrastructure.gateway.repository;

import com.tubadev.receivables.domain.conversation.ConversationId;
import com.tubadev.receivables.domain.message.Direction;
import com.tubadev.receivables.domain.message.Message;
import com.tubadev.receivables.domain.message.MessageContent;
import com.tubadev.receivables.domain.message.MessageGateway;
import com.tubadev.receivables.domain.message.MessageId;
import com.tubadev.receivables.domain.utils.IdUtils;
import com.tubadev.receivables.infrastructure.jdbc.DatabaseClient;
import com.tubadev.receivables.infrastructure.jdbc.JdbcUtils;
import com.tubadev.receivables.infrastructure.jdbc.RowMap;
import com.tubadev.receivables.infrastructure.json.Json;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MessageJdbcRepository implements MessageGateway {

    private static final String COLUMNS =
            "id, version, conversation_id, direction, wamid, content_type, content, status, failure_reason, created_at, updated_at";

    private final DatabaseClient database;
    private final EventJdbcRepository eventRepository;

    public MessageJdbcRepository(final DatabaseClient databaseClient, final EventJdbcRepository eventRepository) {
        this.database = Objects.requireNonNull(databaseClient);
        this.eventRepository = Objects.requireNonNull(eventRepository);
    }

    @Override
    public MessageId nextId() {
        return new MessageId(IdUtils.uniqueId());
    }

    @Override
    public Optional<Message> messageOfId(final MessageId anId) {
        return this.database.queryOne("SELECT %s FROM messages WHERE id = :id".formatted(COLUMNS),
                Map.of("id", anId.value()), messageMapper());
    }

    @Override
    public Optional<Message> messageOfWamid(final String aWamid) {
        return this.database.queryOne("SELECT %s FROM messages WHERE wamid = :wamid".formatted(COLUMNS),
                Map.of("wamid", aWamid), messageMapper());
    }

    @Override
    public List<Message> messagesOfConversation(final ConversationId aConversationId) {
        return this.database.query("SELECT %s FROM messages WHERE conversation_id = :conversationId ORDER BY created_at, id".formatted(COLUMNS),
                Map.of("conversationId", aConversationId.value()), messageMapper());
    }

    @Override
    @Transactional
    public Message save(final Message aMessage) {
        if (aMessage.version() == 0) {
            create(aMessage);
        } else {
            update(aMessage);
        }
        this.eventRepository.saveAll(aMessage.domainEvents());
        return persisted(aMessage);
    }

    private static Message persisted(final Message m) {
        return Message.with(m.id(), m.version() + 1, m.conversationId(), m.direction(), m.wamid(), m.content(),
                m.status().value(), m.failureReason(), m.createdAt(), m.updatedAt());
    }

    private void create(final Message aMessage) {
        final var sql = """
                INSERT INTO messages (id, version, conversation_id, direction, wamid, content_type, content, status, failure_reason, created_at, updated_at)
                VALUES (:id, (:version + 1), :conversationId, :direction, :wamid, :contentType, :content, :status, :failureReason, :createdAt, :updatedAt)
                """;
        executeUpdate(sql, aMessage);
    }

    private void update(final Message aMessage) {
        final var sql = """
                UPDATE messages SET
                    version = (:version + 1),
                    wamid = :wamid,
                    status = :status,
                    failure_reason = :failureReason,
                    updated_at = :updatedAt
                WHERE id = :id AND version = :version
                """;
        if (executeUpdate(sql, aMessage) == 0) {
            throw new OptimisticLockingFailureException("Message %s with version %s was not found"
                    .formatted(aMessage.id().value(), aMessage.version()));
        }
    }

    private int executeUpdate(final String sql, final Message m) {
        final var params = new HashMap<String, Object>();
        params.put("id", m.id().value());
        params.put("version", m.version());
        params.put("conversationId", m.conversationId().value());
        params.put("direction", m.direction().name());
        params.put("wamid", m.wamid());
        params.put("contentType", m.content().type());
        params.put("content", Json.writeValueAsString(m.content()));
        params.put("status", m.status().value());
        params.put("failureReason", m.failureReason());
        params.put("createdAt", JdbcUtils.toTimestamp(m.createdAt()));
        params.put("updatedAt", JdbcUtils.toTimestamp(m.updatedAt()));
        return this.database.update(sql, params);
    }

    private static RowMap<Message> messageMapper() {
        return rs -> Message.with(
                new MessageId(rs.getString("id")),
                rs.getInt("version"),
                new ConversationId(rs.getString("conversation_id")),
                Direction.valueOf(rs.getString("direction")),
                rs.getString("wamid"),
                Json.readValue(rs.getString("content"), MessageContent.typeOf(rs.getString("content_type"))),
                rs.getString("status"),
                rs.getString("failure_reason"),
                JdbcUtils.getInstant(rs, "created_at"),
                JdbcUtils.getInstant(rs, "updated_at")
        );
    }
}
