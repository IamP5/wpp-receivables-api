package com.tubadev.receivables.infrastructure.gateway.repository;

import com.tubadev.receivables.domain.campaign.CampaignId;
import com.tubadev.receivables.domain.conversation.Conversation;
import com.tubadev.receivables.domain.conversation.ConversationGateway;
import com.tubadev.receivables.domain.conversation.ConversationId;
import com.tubadev.receivables.domain.conversation.journey.JourneyStage;
import com.tubadev.receivables.domain.customer.CustomerId;
import com.tubadev.receivables.domain.person.PhoneNumber;
import com.tubadev.receivables.domain.utils.IdUtils;
import com.tubadev.receivables.infrastructure.jdbc.DatabaseClient;
import com.tubadev.receivables.infrastructure.jdbc.JdbcUtils;
import com.tubadev.receivables.infrastructure.jdbc.RowMap;
import com.tubadev.receivables.infrastructure.json.Json;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
public class ConversationJdbcRepository implements ConversationGateway {

    private static final String COLUMNS =
            "id, version, phone_number, customer_id, campaign_id, stage, stage_data, last_inbound_at, created_at, updated_at";

    private final DatabaseClient database;
    private final EventJdbcRepository eventRepository;

    public ConversationJdbcRepository(final DatabaseClient databaseClient, final EventJdbcRepository eventRepository) {
        this.database = Objects.requireNonNull(databaseClient);
        this.eventRepository = Objects.requireNonNull(eventRepository);
    }

    @Override
    public ConversationId nextId() {
        return new ConversationId(IdUtils.uniqueId());
    }

    @Override
    public Optional<Conversation> conversationOfId(final ConversationId anId) {
        final var sql = "SELECT %s FROM conversations WHERE id = :id".formatted(COLUMNS);
        return this.database.queryOne(sql, Map.of("id", anId.value()), conversationMapper());
    }

    @Override
    public Optional<Conversation> openConversationOf(final PhoneNumber aPhoneNumber) {
        final var sql = """
                SELECT %s FROM conversations
                WHERE phone_number IN (:phones) AND is_open = true
                ORDER BY created_at DESC
                LIMIT 1
                """.formatted(COLUMNS);
        return this.database.queryOne(sql, Map.of("phones", aPhoneNumber.equivalents()), conversationMapper());
    }

    @Override
    @Transactional
    public Conversation save(final Conversation aConversation) {
        if (aConversation.version() == 0) {
            create(aConversation);
        } else {
            update(aConversation);
        }
        this.eventRepository.saveAll(aConversation.domainEvents());
        return persisted(aConversation);
    }

    /** The stored state: next version and no pending events, so the caller can keep working on it and save again. */
    private static Conversation persisted(final Conversation c) {
        return Conversation.with(c.id(), c.version() + 1, c.phoneNumber(), c.customerId(), c.campaignId(), c.stage(),
                c.lastInboundAt(), c.createdAt(), c.updatedAt());
    }

    private void create(final Conversation aConversation) {
        final var sql = """
                INSERT INTO conversations (id, version, phone_number, customer_id, campaign_id, stage, stage_data, is_open, last_inbound_at, created_at, updated_at)
                VALUES (:id, (:version + 1), :phoneNumber, :customerId, :campaignId, :stage, :stageData, :open, :lastInboundAt, :createdAt, :updatedAt)
                """;
        executeUpdate(sql, aConversation);
    }

    private void update(final Conversation aConversation) {
        final var sql = """
                UPDATE conversations SET
                    version = (:version + 1),
                    customer_id = :customerId,
                    stage = :stage,
                    stage_data = :stageData,
                    is_open = :open,
                    last_inbound_at = :lastInboundAt,
                    updated_at = :updatedAt
                WHERE id = :id AND version = :version
                """;
        if (executeUpdate(sql, aConversation) == 0) {
            throw new OptimisticLockingFailureException("Conversation %s with version %s was not found"
                    .formatted(aConversation.id().value(), aConversation.version()));
        }
    }

    private int executeUpdate(final String sql, final Conversation c) {
        final var params = new HashMap<String, Object>();
        params.put("id", c.id().value());
        params.put("version", c.version());
        params.put("phoneNumber", c.phoneNumber().value());
        params.put("customerId", c.customerId() == null ? null : c.customerId().value());
        params.put("campaignId", c.campaignId() == null ? null : c.campaignId().value());
        params.put("stage", c.stage().value());
        params.put("stageData", Json.writeValueAsString(c.stage()));
        params.put("open", c.isOpen());
        params.put("lastInboundAt", JdbcUtils.toTimestamp(c.lastInboundAt()));
        params.put("createdAt", JdbcUtils.toTimestamp(c.createdAt()));
        params.put("updatedAt", JdbcUtils.toTimestamp(c.updatedAt()));
        return this.database.update(sql, params);
    }

    private static RowMap<Conversation> conversationMapper() {
        return rs -> {
            final var customerId = rs.getString("customer_id");
            final var campaignId = rs.getString("campaign_id");
            return Conversation.with(
                    new ConversationId(rs.getString("id")),
                    rs.getInt("version"),
                    new PhoneNumber(rs.getString("phone_number")),
                    customerId == null ? null : new CustomerId(customerId),
                    campaignId == null ? null : new CampaignId(campaignId),
                    Json.readValue(rs.getString("stage_data"), JourneyStage.typeOf(rs.getString("stage"))),
                    JdbcUtils.getInstant(rs, "last_inbound_at"),
                    JdbcUtils.getInstant(rs, "created_at"),
                    JdbcUtils.getInstant(rs, "updated_at")
            );
        };
    }
}
