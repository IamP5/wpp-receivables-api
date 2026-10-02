package com.tubadev.receivables.infrastructure.gateway.repository;

import com.tubadev.receivables.AbstractRepositoryTest;
import com.tubadev.receivables.domain.Fixture;
import com.tubadev.receivables.domain.conversation.ConversationCommand.AdvanceTo;
import com.tubadev.receivables.domain.conversation.ConversationCommand.RegisterInbound;
import com.tubadev.receivables.domain.conversation.ConversationEnded;
import com.tubadev.receivables.domain.conversation.ConversationStarted;
import com.tubadev.receivables.domain.conversation.journey.JourneyStage;
import com.tubadev.receivables.domain.person.PhoneNumber;
import com.tubadev.receivables.domain.receivable.Money;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

class ConversationJdbcRepositoryTest extends AbstractRepositoryTest {

    @Test
    void givenNewConversation_whenSaved_shouldPersistStageDataAndEvents() {
        final var receivedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        final var aConversation = Fixture.Conversations.withInbound(Fixture.Customers.maria(), receivedAt);
        aConversation.execute(new AdvanceTo(new JourneyStage.AwaitingAmount(Money.brl("15230.50"))));

        conversationRepository().save(aConversation);

        final var actual = conversationRepository().conversationOfId(aConversation.id()).orElseThrow();
        Assertions.assertEquals(1, actual.version());
        Assertions.assertEquals(new JourneyStage.AwaitingAmount(Money.brl("15230.50")), actual.stage());
        Assertions.assertEquals(receivedAt, actual.lastInboundAt());
        Assertions.assertEquals(aConversation.customerId(), actual.customerId());

        final var events = eventRepository().eventsOfAggregate(aConversation.id().value());
        Assertions.assertInstanceOf(ConversationStarted.class, events.getFirst());
        Assertions.assertEquals(2, events.size());
    }

    @Test
    void givenBrazilianNumberWithoutNinthDigit_whenSearchingOpenConversation_shouldFindIt() {
        final var aConversation = Fixture.Conversations.of(Fixture.Customers.maria()); // 5511988887777
        conversationRepository().save(aConversation);

        final var actual = conversationRepository().openConversationOf(new PhoneNumber("551188887777"));

        Assertions.assertEquals(aConversation.id(), actual.orElseThrow().id());
    }

    @Test
    void givenFinishedConversation_whenSearchingOpenConversation_shouldIgnoreIt() {
        final var aConversation = Fixture.Conversations.of(Fixture.Customers.maria());
        conversationRepository().save(aConversation);

        final var loaded = conversationRepository().conversationOfId(aConversation.id()).orElseThrow();
        loaded.execute(new AdvanceTo(new JourneyStage.Closed("offer_declined")));
        conversationRepository().save(loaded);

        Assertions.assertTrue(conversationRepository().openConversationOf(aConversation.phoneNumber()).isEmpty());
        Assertions.assertInstanceOf(ConversationEnded.class, eventRepository().eventsOfAggregate(aConversation.id().value()).getLast());
    }

    @Test
    void givenStaleVersion_whenSaving_shouldFailWithOptimisticLock() {
        final var aConversation = Fixture.Conversations.of(Fixture.Customers.maria());
        conversationRepository().save(aConversation);

        final var first = conversationRepository().conversationOfId(aConversation.id()).orElseThrow();
        final var second = conversationRepository().conversationOfId(aConversation.id()).orElseThrow();
        first.execute(new RegisterInbound(Instant.now()));
        conversationRepository().save(first);
        second.execute(new RegisterInbound(Instant.now()));

        Assertions.assertThrows(OptimisticLockingFailureException.class, () -> conversationRepository().save(second));
    }
}
