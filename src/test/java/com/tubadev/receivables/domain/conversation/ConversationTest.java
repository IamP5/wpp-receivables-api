package com.tubadev.receivables.domain.conversation;

import com.tubadev.receivables.domain.Fixture;
import com.tubadev.receivables.domain.UnitTest;
import com.tubadev.receivables.domain.conversation.ConversationCommand.AdvanceTo;
import com.tubadev.receivables.domain.conversation.ConversationCommand.Expire;
import com.tubadev.receivables.domain.conversation.ConversationCommand.RegisterInbound;
import com.tubadev.receivables.domain.conversation.ConversationCommand.RegisterOutbound;
import com.tubadev.receivables.domain.conversation.ConversationCommand.Release;
import com.tubadev.receivables.domain.conversation.journey.JourneyStage;
import com.tubadev.receivables.domain.exceptions.DomainException;
import com.tubadev.receivables.domain.message.MessageContent;
import com.tubadev.receivables.domain.receivable.Money;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

class ConversationTest extends UnitTest {

    private static final SessionPolicy POLICY = new SessionPolicy(Duration.ofMinutes(30), Duration.ofHours(2));

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

    @Test
    void givenBotStage_whenCustomerSilentLongerThanIdleTimeout_shouldBeIdle() {
        final var lastInbound = Instant.parse("2026-10-01T15:00:00Z");
        final var aConversation = Fixture.Conversations.withInbound(Fixture.Customers.maria(), lastInbound);
        aConversation.execute(new AdvanceTo(new JourneyStage.AwaitingAmount(Money.brl("1000"))));

        Assertions.assertFalse(aConversation.isIdle(lastInbound.plus(Duration.ofMinutes(29)), POLICY));
        Assertions.assertTrue(aConversation.isIdle(lastInbound.plus(Duration.ofMinutes(30)), POLICY));
    }

    @Test
    void givenCampaignNeverAnswered_shouldNotBeIdle() {
        final var aConversation = Fixture.Conversations.of(Fixture.Customers.maria());
        aConversation.execute(new RegisterOutbound(Instant.parse("2026-09-01T12:00:00Z")));

        Assertions.assertFalse(aConversation.isIdle(Instant.parse("2026-10-01T12:00:00Z"), POLICY));
    }

    @Test
    void givenHandoff_whenOnlyTheCustomerKeepsWriting_shouldExpireAfterHandoffTimeout() {
        final var handoffAt = Instant.parse("2026-10-01T15:00:00Z");
        final var aConversation = Fixture.Conversations.withInbound(Fixture.Customers.maria(), handoffAt);
        aConversation.execute(new AdvanceTo(new JourneyStage.HumanHandoff("biometrics_failed")), new RegisterOutbound(handoffAt));
        aConversation.execute(new RegisterInbound(handoffAt.plus(Duration.ofMinutes(119))));

        Assertions.assertFalse(aConversation.isIdle(handoffAt.plus(Duration.ofMinutes(119)), POLICY));
        Assertions.assertTrue(aConversation.isIdle(handoffAt.plus(Duration.ofHours(2)), POLICY));

        aConversation.execute(new RegisterOutbound(handoffAt.plus(Duration.ofMinutes(90))));
        Assertions.assertFalse(aConversation.isIdle(handoffAt.plus(Duration.ofHours(2)), POLICY));
    }

    @Test
    void givenIdleSession_whenExpired_shouldCloseWithReasonAndEndEvent() {
        final var bot = Fixture.Conversations.withInbound(Fixture.Customers.maria(), Instant.now());
        final var handoff = Fixture.Conversations.withInbound(Fixture.Customers.maria(), Instant.now());
        handoff.execute(new AdvanceTo(new JourneyStage.HumanHandoff("unknown_customer")));

        bot.execute(new Expire());
        handoff.execute(new Expire());

        Assertions.assertEquals(new JourneyStage.Closed(JourneyStage.Closed.INACTIVITY), bot.stage());
        Assertions.assertEquals(new JourneyStage.Closed(JourneyStage.Closed.HANDOFF_TIMEOUT), handoff.stage());
        Assertions.assertFalse(bot.isOpen());
        Assertions.assertInstanceOf(ConversationEnded.class, bot.domainEvents().getLast());
        Assertions.assertFalse(bot.isIdle(Instant.now().plus(Duration.ofDays(1)), POLICY));
    }

    @Test
    void givenHandoff_whenReleased_shouldClose() {
        final var aConversation = Fixture.Conversations.withInbound(Fixture.Customers.maria(), Instant.now());
        aConversation.execute(new AdvanceTo(new JourneyStage.HumanHandoff("agent_took_over")));

        aConversation.execute(new Release());

        Assertions.assertEquals(new JourneyStage.Closed(JourneyStage.Closed.HANDOFF_RELEASED), aConversation.stage());
    }

    @Test
    void givenBotStage_whenReleased_shouldReject() {
        final var aConversation = Fixture.Conversations.withInbound(Fixture.Customers.maria(), Instant.now());
        aConversation.execute(new AdvanceTo(new JourneyStage.MainMenu()));

        final var ex = Assertions.assertThrows(DomainException.class, () -> aConversation.execute(new Release()));

        Assertions.assertTrue(ex.getMessage().contains("is not with an agent"));
    }

    @Test
    void givenOutboundsOutOfOrder_shouldKeepTheLatest() {
        final var aConversation = Fixture.Conversations.of(Fixture.Customers.maria());
        final var latest = Instant.parse("2026-10-01T15:00:00Z");

        aConversation.execute(new RegisterOutbound(latest), new RegisterOutbound(latest.minusSeconds(5)));

        Assertions.assertEquals(latest, aConversation.lastOutboundAt());
    }
}
