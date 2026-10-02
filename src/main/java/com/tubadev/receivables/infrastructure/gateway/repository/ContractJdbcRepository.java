package com.tubadev.receivables.infrastructure.gateway.repository;

import com.tubadev.receivables.domain.contract.Contract;
import com.tubadev.receivables.domain.contract.ContractDocument;
import com.tubadev.receivables.domain.contract.ContractGateway;
import com.tubadev.receivables.domain.contract.ContractId;
import com.tubadev.receivables.domain.contract.ElectronicSignature;
import com.tubadev.receivables.domain.contract.Party;
import com.tubadev.receivables.domain.conversation.ConversationId;
import com.tubadev.receivables.domain.customer.CustomerId;
import com.tubadev.receivables.domain.receivable.AnticipationOffer;
import com.tubadev.receivables.domain.utils.IdUtils;
import com.tubadev.receivables.infrastructure.jdbc.DatabaseClient;
import com.tubadev.receivables.infrastructure.jdbc.JdbcUtils;
import com.tubadev.receivables.infrastructure.json.Json;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The contract terms (assignor, offer and signature) are stored as JSON snapshots: they never change after issue.
 */
@Repository
public class ContractJdbcRepository implements ContractGateway {

    private final DatabaseClient database;
    private final EventJdbcRepository eventRepository;

    public ContractJdbcRepository(final DatabaseClient databaseClient, final EventJdbcRepository eventRepository) {
        this.database = Objects.requireNonNull(databaseClient);
        this.eventRepository = Objects.requireNonNull(eventRepository);
    }

    @Override
    public ContractId nextId() {
        return new ContractId(IdUtils.uniqueId());
    }

    @Override
    public Optional<Contract> contractOfId(final ContractId anId) {
        final var sql = """
                SELECT id, version, protocol, customer_id, conversation_id, assignor, offer, expected_credit_date, signature,
                       authentication_code, document_filename, document_sha256, issued_at, updated_at
                FROM contracts WHERE id = :id
                """;
        return this.database.queryOne(sql, Map.of("id", anId.value()), rs -> Contract.with(
                new ContractId(rs.getString("id")),
                rs.getInt("version"),
                rs.getString("protocol"),
                new CustomerId(rs.getString("customer_id")),
                new ConversationId(rs.getString("conversation_id")),
                Json.readValue(rs.getString("assignor"), Party.class),
                Json.readValue(rs.getString("offer"), AnticipationOffer.class),
                rs.getDate("expected_credit_date").toLocalDate(),
                Json.readValue(rs.getString("signature"), ElectronicSignature.class),
                rs.getString("authentication_code"),
                rs.getString("document_filename"),
                rs.getString("document_sha256"),
                JdbcUtils.getInstant(rs, "issued_at"),
                JdbcUtils.getInstant(rs, "updated_at")
        ));
    }

    @Override
    @Transactional
    public Contract save(final Contract aContract) {
        if (aContract.version() == 0) {
            create(aContract);
        } else {
            update(aContract);
        }
        this.eventRepository.saveAll(aContract.domainEvents());
        return persisted(aContract);
    }

    @Override
    public void saveDocument(final ContractId anId, final ContractDocument aDocument) {
        this.database.update("UPDATE contracts SET document = :document WHERE id = :id",
                Map.of("id", anId.value(), "document", aDocument.content()));
    }

    @Override
    public Optional<ContractDocument> documentOf(final ContractId anId) {
        final var sql = "SELECT document, document_filename FROM contracts WHERE id = :id AND document IS NOT NULL";
        return this.database.queryOne(sql, Map.of("id", anId.value()),
                rs -> new ContractDocument(rs.getBytes("document"), rs.getString("document_filename")));
    }

    private static Contract persisted(final Contract c) {
        return Contract.with(c.id(), c.version() + 1, c.protocol(), c.customerId(), c.conversationId(), c.assignor(), c.offer(),
                c.expectedCreditDate(), c.signature(), c.authenticationCode(), c.documentFilename(), c.documentSha256(),
                c.issuedAt(), c.updatedAt());
    }

    private void create(final Contract aContract) {
        final var sql = """
                INSERT INTO contracts (id, version, protocol, customer_id, conversation_id, assignor, offer, expected_credit_date,
                                       signature, authentication_code, document_filename, document_sha256, issued_at, updated_at)
                VALUES (:id, (:version + 1), :protocol, :customerId, :conversationId, :assignor, :offer, :expectedCreditDate,
                        :signature, :authenticationCode, :documentFilename, :documentSha256, :issuedAt, :updatedAt)
                """;
        executeUpdate(sql, aContract);
    }

    private void update(final Contract aContract) {
        final var sql = """
                UPDATE contracts SET
                    version = (:version + 1),
                    document_filename = :documentFilename,
                    document_sha256 = :documentSha256,
                    updated_at = :updatedAt
                WHERE id = :id AND version = :version
                """;
        if (executeUpdate(sql, aContract) == 0) {
            throw new OptimisticLockingFailureException("Contract %s with version %s was not found"
                    .formatted(aContract.id().value(), aContract.version()));
        }
    }

    private int executeUpdate(final String sql, final Contract c) {
        final var params = new HashMap<String, Object>();
        params.put("id", c.id().value());
        params.put("version", c.version());
        params.put("protocol", c.protocol());
        params.put("customerId", c.customerId().value());
        params.put("conversationId", c.conversationId().value());
        params.put("assignor", Json.writeValueAsString(c.assignor()));
        params.put("offer", Json.writeValueAsString(c.offer()));
        params.put("expectedCreditDate", Date.valueOf(c.expectedCreditDate()));
        params.put("signature", Json.writeValueAsString(c.signature()));
        params.put("authenticationCode", c.authenticationCode());
        params.put("documentFilename", c.documentFilename());
        params.put("documentSha256", c.documentSha256());
        params.put("issuedAt", JdbcUtils.toTimestamp(c.issuedAt()));
        params.put("updatedAt", JdbcUtils.toTimestamp(c.updatedAt()));
        return this.database.update(sql, params);
    }
}
