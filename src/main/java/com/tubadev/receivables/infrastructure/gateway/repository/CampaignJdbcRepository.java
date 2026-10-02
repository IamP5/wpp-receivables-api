package com.tubadev.receivables.infrastructure.gateway.repository;

import com.tubadev.receivables.domain.campaign.Campaign;
import com.tubadev.receivables.domain.campaign.CampaignGateway;
import com.tubadev.receivables.domain.campaign.CampaignId;
import com.tubadev.receivables.domain.campaign.CampaignTemplate;
import com.tubadev.receivables.domain.campaign.DispatchStats;
import com.tubadev.receivables.domain.customer.CustomerId;
import com.tubadev.receivables.domain.utils.IdUtils;
import com.tubadev.receivables.infrastructure.jdbc.DatabaseClient;
import com.tubadev.receivables.infrastructure.jdbc.JdbcUtils;
import com.tubadev.receivables.infrastructure.json.Json;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
public class CampaignJdbcRepository implements CampaignGateway {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final DatabaseClient database;
    private final EventJdbcRepository eventRepository;

    public CampaignJdbcRepository(final DatabaseClient databaseClient, final EventJdbcRepository eventRepository) {
        this.database = Objects.requireNonNull(databaseClient);
        this.eventRepository = Objects.requireNonNull(eventRepository);
    }

    @Override
    public CampaignId nextId() {
        return new CampaignId(IdUtils.uniqueId());
    }

    @Override
    public Optional<Campaign> campaignOfId(final CampaignId anId) {
        final var recipients = this.database.query(
                "SELECT customer_id FROM campaign_recipients WHERE campaign_id = :id ORDER BY position",
                Map.of("id", anId.value()),
                rs -> new CustomerId(rs.getString("customer_id"))
        );

        final var sql = """
                SELECT id, version, name, template_name, template_language, template_parameters, status,
                       sent, failed, skipped, created_at, updated_at, started_at, finished_at
                FROM campaigns WHERE id = :id
                """;
        return this.database.queryOne(sql, Map.of("id", anId.value()), rs -> Campaign.with(
                new CampaignId(rs.getString("id")),
                rs.getInt("version"),
                rs.getString("name"),
                CampaignTemplate.of(
                        rs.getString("template_name"),
                        rs.getString("template_language"),
                        Json.readValue(rs.getString("template_parameters"), STRING_LIST)
                ),
                recipients,
                rs.getString("status"),
                new DispatchStats(rs.getInt("sent"), rs.getInt("failed"), rs.getInt("skipped")),
                JdbcUtils.getInstant(rs, "created_at"),
                JdbcUtils.getInstant(rs, "updated_at"),
                JdbcUtils.getInstant(rs, "started_at"),
                JdbcUtils.getInstant(rs, "finished_at")
        ));
    }

    @Override
    @Transactional
    public Campaign save(final Campaign aCampaign) {
        if (aCampaign.version() == 0) {
            create(aCampaign);
        } else {
            update(aCampaign);
        }
        this.eventRepository.saveAll(aCampaign.domainEvents());
        return persisted(aCampaign);
    }

    private static Campaign persisted(final Campaign c) {
        return Campaign.with(c.id(), c.version() + 1, c.name(), c.template(), c.recipients(), c.status().value(), c.stats(),
                c.createdAt(), c.updatedAt(), c.startedAt(), c.finishedAt());
    }

    private void create(final Campaign aCampaign) {
        final var sql = """
                INSERT INTO campaigns (id, version, name, template_name, template_language, template_parameters, status,
                                       sent, failed, skipped, created_at, updated_at, started_at, finished_at)
                VALUES (:id, (:version + 1), :name, :templateName, :templateLanguage, :templateParameters, :status,
                        :sent, :failed, :skipped, :createdAt, :updatedAt, :startedAt, :finishedAt)
                """;
        executeUpdate(sql, aCampaign);

        var position = 0;
        for (var recipient : aCampaign.recipients()) {
            this.database.update(
                    "INSERT INTO campaign_recipients (campaign_id, customer_id, position) VALUES (:campaignId, :customerId, :position)",
                    Map.of("campaignId", aCampaign.id().value(), "customerId", recipient.value(), "position", position++)
            );
        }
    }

    private void update(final Campaign aCampaign) {
        final var sql = """
                UPDATE campaigns SET
                    version = (:version + 1),
                    status = :status,
                    sent = :sent,
                    failed = :failed,
                    skipped = :skipped,
                    updated_at = :updatedAt,
                    started_at = :startedAt,
                    finished_at = :finishedAt
                WHERE id = :id AND version = :version
                """;
        if (executeUpdate(sql, aCampaign) == 0) {
            throw new OptimisticLockingFailureException("Campaign %s with version %s was not found"
                    .formatted(aCampaign.id().value(), aCampaign.version()));
        }
    }

    private int executeUpdate(final String sql, final Campaign c) {
        final var params = new HashMap<String, Object>();
        params.put("id", c.id().value());
        params.put("version", c.version());
        params.put("name", c.name());
        params.put("templateName", c.template().name());
        params.put("templateLanguage", c.template().languageCode());
        params.put("templateParameters", Json.writeValueAsString(c.template().rawParameters()));
        params.put("status", c.status().value());
        params.put("sent", c.stats().sent());
        params.put("failed", c.stats().failed());
        params.put("skipped", c.stats().skipped());
        params.put("createdAt", JdbcUtils.toTimestamp(c.createdAt()));
        params.put("updatedAt", JdbcUtils.toTimestamp(c.updatedAt()));
        params.put("startedAt", JdbcUtils.toTimestamp(c.startedAt()));
        params.put("finishedAt", JdbcUtils.toTimestamp(c.finishedAt()));
        return this.database.update(sql, params);
    }
}
