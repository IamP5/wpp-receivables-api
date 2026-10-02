package com.tubadev.receivables.application.conversation.impl;

import com.tubadev.receivables.application.UseCaseTest;
import com.tubadev.receivables.application.message.SendMessage;
import com.tubadev.receivables.domain.Fixture;
import com.tubadev.receivables.domain.conversation.Conversation;
import com.tubadev.receivables.domain.conversation.ConversationCommand.AdvanceTo;
import com.tubadev.receivables.domain.conversation.ConversationGateway;
import com.tubadev.receivables.domain.conversation.ConversationId;
import com.tubadev.receivables.domain.conversation.journey.JourneyStage;
import com.tubadev.receivables.domain.exceptions.DomainException;
import com.tubadev.receivables.domain.exceptions.NotFoundException;
import com.tubadev.receivables.domain.message.MessageId;
import com.tubadev.receivables.domain.message.status.MessageStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DefaultReleaseConversationTest extends UseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Mock
    private ConversationGateway conversationGateway;

    @Mock
    private SendMessage sendMessage;

    private DefaultReleaseConversation target;

    record TestInput(String conversationId) implements DefaultReleaseConversation.Input {}

    record Sent(MessageId messageId, String wamid, String status, String failureReason) implements SendMessage.Output {}

    @BeforeEach
    void setUp() {
        target = new DefaultReleaseConversation(Clock.fixed(NOW, ZoneOffset.UTC), conversationGateway, sendMessage);
    }

    @Test
    void givenHandoffWithinWindow_whenReleased_shouldCloseAndTellTheCustomer() {
        final var aConversation = handoff(NOW.minus(Duration.ofHours(3)));
        when(conversationGateway.save(any())).thenAnswer(returnsFirstArg());
        when(sendMessage.execute(any())).thenReturn(new Sent(new MessageId("msg-1"), "wamid.1", MessageStatus.ACCEPTED, null));

        final var out = target.execute(new TestInput(aConversation.id().value()));

        Assertions.assertEquals(JourneyStage.CLOSED, out.stage());
        Assertions.assertTrue(out.customerNotified());
        Assertions.assertEquals(new JourneyStage.Closed(JourneyStage.Closed.HANDOFF_RELEASED), aConversation.stage());
        verify(sendMessage).execute(argThat((SendMessage.Input in) -> in.content().toString().contains("oi")));
    }

    @Test
    void givenHandoffOutsideWindow_whenReleased_shouldCloseWithoutMessaging() {
        final var aConversation = handoff(NOW.minus(Duration.ofHours(25)));
        when(conversationGateway.save(any())).thenAnswer(returnsFirstArg());

        final var out = target.execute(new TestInput(aConversation.id().value()));

        Assertions.assertFalse(out.customerNotified());
        Assertions.assertFalse(aConversation.isOpen());
        verifyNoInteractions(sendMessage);
    }

    @Test
    void givenBotStage_whenReleased_shouldRejectAndKeepIt() {
        final var aConversation = Fixture.Conversations.withInbound(Fixture.Customers.maria(), NOW);
        when(conversationGateway.conversationOfId(aConversation.id())).thenReturn(Optional.of(aConversation));

        Assertions.assertThrows(DomainException.class, () -> target.execute(new TestInput(aConversation.id().value())));

        verify(conversationGateway, never()).save(any());
        verifyNoInteractions(sendMessage);
    }

    @Test
    void givenUnknownConversation_shouldThrowNotFound() {
        when(conversationGateway.conversationOfId(new ConversationId("nope"))).thenReturn(Optional.empty());

        Assertions.assertThrows(NotFoundException.class, () -> target.execute(new TestInput("nope")));
    }

    private Conversation handoff(final Instant lastInbound) {
        final var aConversation = Fixture.Conversations.withInbound(Fixture.Customers.maria(), lastInbound);
        aConversation.execute(new AdvanceTo(new JourneyStage.HumanHandoff("biometrics_failed")));
        when(conversationGateway.conversationOfId(aConversation.id())).thenReturn(Optional.of(aConversation));
        return aConversation;
    }
}
