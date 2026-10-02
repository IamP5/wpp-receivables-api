package com.tubadev.receivables.domain.conversation;

import com.tubadev.receivables.domain.Fixture;
import com.tubadev.receivables.domain.UnitTest;
import com.tubadev.receivables.domain.conversation.ConversationCommand.AdvanceTo;
import com.tubadev.receivables.domain.conversation.ConversationCommand.RegisterInbound;
import com.tubadev.receivables.domain.conversation.journey.JourneyStage;
import com.tubadev.receivables.domain.exceptions.DomainException;
import com.tubadev.receivables.domain.message.MessageContent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

class ConversationTest extends UnitTest {

    @Test
    void givenNewConversation_shouldStartJourneyAndRegisterEvent() {
        final var aConversation = Fixture.Conversations.of(Fixture.Customers.maria());

        Assertions.assertInstanceOf(JourneyStage.Started.class, aConversation.stage());
        Assertions.assertTrue(aConversation.isOpen());
        Assertions.assertEquals(1, aConversation.domainEvents().size());
        Assertions.assertInstanceOf(ConversationStarted.class, aConversation.domainEvents().getFirst());
    }

    @Test
    void givenInboundWithin24h_shouldAllowFreeFormMessages() {
        final var now = Instant.now();
        final var aConversation = Fixture.Conversations.withInbound(Fixture.Customers.maria(), now.minus(Duration.ofHours(23)));

        Assertions.assertTrue(aConversation.isWithinServiceWindow(now));
        Assertions.assertDoesNotThrow(() -> aConversation.assertCanSend(new MessageContent.Text("oi"), now));
    }

    @Test
    void givenInboundOlderThan24h_shouldOnlyAllowTemplates() {
        final var now = Instant.now();
        final var aConversation = Fixture.Conversations.withInbound(Fixture.Customers.maria(), now.minus(Duration.ofHours(25)));

        Assertions.assertFalse(aConversation.isWithinServiceWindow(now));
        Assertions.assertThrows(DomainException.class, () -> aConversation.assertCanSend(new MessageContent.Text("oi"), now));
        Assertions.assertDoesNotThrow(() -> aConversation.assertCanSend(new MessageContent.Template("hello_world", "en_US", List.of()), now));
    }

    @Test
    void givenOlderInbound_whenRegistering_shouldKeepLatestTimestamp() {
        final var now = Instant.now();
        final var aConversation = Fixture.Conversations.withInbound(Fixture.Customers.maria(), now);

        aConversation.execute(new RegisterInbound(now.minusSeconds(60)));

        Assertions.assertEquals(now, aConversation.lastInboundAt());
    }

    @Test
    void givenValidTransition_whenAdvancing_shouldRegisterJourneyAdvanced() {
        final var aConversation = Fixture.Conversations.of(Fixture.Customers.maria());

        aConversation.execute(new AdvanceTo(new JourneyStage.MainMenu()));

        Assertions.assertInstanceOf(JourneyStage.MainMenu.class, aConversation.stage());
        final var event = (JourneyAdvanced) aConversation.domainEvents().getLast();
        Assertions.assertEquals(JourneyStage.STARTED, event.from());
        Assertions.assertEquals(JourneyStage.MAIN_MENU, event.to());
    }

    @Test
    void givenTerminalStage_whenAdvancing_shouldEndConversation() {
        final var aConversation = Fixture.Conversations.of(Fixture.Customers.maria());

        aConversation.execute(new AdvanceTo(new JourneyStage.Closed("opted_out")));

        Assertions.assertFalse(aConversation.isOpen());
        final var ended = (ConversationEnded) aConversation.domainEvents().getLast();
        Assertions.assertEquals(JourneyStage.CLOSED, ended.outcome());
        Assertions.assertEquals("opted_out", ended.detail());
    }

    @Test
    void givenInvalidTransition_whenAdvancing_shouldThrow() {
        final var aConversation = Fixture.Conversations.of(Fixture.Customers.maria());

        final var ex = Assertions.assertThrows(DomainException.class,
                () -> aConversation.execute(new AdvanceTo(new JourneyStage.Completed("ANT-1"))));

        Assertions.assertEquals("Journey can´t advance from started to completed", ex.getMessage());
    }
}
