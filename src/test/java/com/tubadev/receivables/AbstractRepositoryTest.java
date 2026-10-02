package com.tubadev.receivables;

import com.tubadev.receivables.infrastructure.gateway.repository.CampaignJdbcRepository;
import com.tubadev.receivables.infrastructure.gateway.repository.ContractJdbcRepository;
import com.tubadev.receivables.infrastructure.gateway.repository.ConversationJdbcRepository;
import com.tubadev.receivables.infrastructure.gateway.repository.EventJdbcRepository;
import com.tubadev.receivables.infrastructure.gateway.repository.MessageJdbcRepository;
import com.tubadev.receivables.infrastructure.jdbc.JdbcClientAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.jdbc.JdbcTestUtils;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test-integration")
@Tag("integrationTest")
public abstract class AbstractRepositoryTest {

    @Autowired
    private JdbcClient jdbcClient;

    private EventJdbcRepository eventRepository;
    private ConversationJdbcRepository conversationRepository;
    private MessageJdbcRepository messageRepository;
    private CampaignJdbcRepository campaignRepository;
    private ContractJdbcRepository contractRepository;

    @BeforeEach
    void setUpRepositories() {
        final var database = new JdbcClientAdapter(jdbcClient);
        this.eventRepository = new EventJdbcRepository(database);
        this.conversationRepository = new ConversationJdbcRepository(database, eventRepository);
        this.messageRepository = new MessageJdbcRepository(database, eventRepository);
        this.campaignRepository = new CampaignJdbcRepository(database, eventRepository);
        this.contractRepository = new ContractJdbcRepository(database, eventRepository);
    }

    protected int countRows(final String table) {
        return JdbcTestUtils.countRowsInTable(jdbcClient, table);
    }

    protected EventJdbcRepository eventRepository() {
        return eventRepository;
    }

    protected ConversationJdbcRepository conversationRepository() {
        return conversationRepository;
    }

    protected MessageJdbcRepository messageRepository() {
        return messageRepository;
    }

    protected CampaignJdbcRepository campaignRepository() {
        return campaignRepository;
    }

    protected ContractJdbcRepository contractRepository() {
        return contractRepository;
    }
}
