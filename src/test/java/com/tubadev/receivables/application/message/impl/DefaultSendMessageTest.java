package com.tubadev.receivables.application.message.impl;

import com.tubadev.receivables.application.UseCaseTest;
import com.tubadev.receivables.application.message.SendMessage;
import com.tubadev.receivables.domain.Fixture;
import com.tubadev.receivables.domain.conversation.ConversationGateway;
import com.tubadev.receivables.domain.conversation.ConversationId;
import com.tubadev.receivables.domain.exceptions.DomainException;
import com.tubadev.receivables.domain.message.MessageContent;
import com.tubadev.receivables.domain.message.MessageGateway;
import com.tubadev.receivables.domain.message.MessageId;
import com.tubadev.receivables.domain.message.MessagingGateway;
import com.tubadev.receivables.domain.message.SendResult;
import com.tubadev.receivables.domain.message.status.MessageStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DefaultSendMessageTest extends UseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Mock
    private ConversationGateway conversationGateway;

    @Mock
    private MessageGateway messageGateway;

    @Mock
    private MessagingGateway messagingGateway;

    private DefaultSendMessage target;

    record TestInput(ConversationId conversationId, MessageContent content) implements SendMessage.Input {}

    @BeforeEach
    void setUp() {
        target = new DefaultSendMessage(Clock.fixed(NOW, ZoneOffset.UTC), conversationGateway, messageGateway, messagingGateway);
    }

    @Test
    void givenOpenWindow_whenAccepted_shouldStoreAcceptedMessage() {
        final var conversation = Fixture.Conversations.withInbound(Fixture.Customers.maria(), NOW.minus(Duration.ofHours(1)));
        when(conversationGateway.conversationOfId(conversation.id())).thenReturn(Optional.of(conversation));
        when(messageGateway.nextId()).thenReturn(new MessageId("msg-1"));
        when(messageGateway.save(any())).thenAnswer(returnsFirstArg());
        when(messagingGateway.send(any(), any())).thenReturn(new SendResult.Accepted("wamid.out"));

        final var out = target.execute(new TestInput(conversation.id(), new MessageContent.Text("Olá")));

        Assertions.assertEquals("wamid.out", out.wamid());
        Assertions.assertEquals(MessageStatus.ACCEPTED, out.status());
        verify(messagingGateway).send(conversation.phoneNumber(), new MessageContent.Text("Olá"));
    }

    @Test
    void givenRejection_shouldStoreFailedMessageWithReason() {
        final var conversation = Fixture.Conversations.withInbound(Fixture.Customers.maria(), NOW.minus(Duration.ofHours(1)));
        when(conversationGateway.conversationOfId(conversation.id())).thenReturn(Optional.of(conversation));
        when(messageGateway.nextId()).thenReturn(new MessageId("msg-1"));
        when(messageGateway.save(any())).thenAnswer(returnsFirstArg());
        when(messagingGateway.send(any(), any())).thenReturn(new SendResult.Rejected("130497", "Business account is restricted"));

        final var out = target.execute(new TestInput(conversation.id(), new MessageContent.Text("Olá")));

        Assertions.assertEquals(MessageStatus.FAILED, out.status());
        Assertions.assertEquals("[130497] Business account is restricted", out.failureReason());
    }

    @Test
    void givenClosedWindow_whenFreeForm_shouldRejectBeforeCallingWhatsApp() {
        final var conversation = Fixture.Conversations.of(Fixture.Customers.maria());
        when(conversationGateway.conversationOfId(conversation.id())).thenReturn(Optional.of(conversation));

        Assertions.assertThrows(DomainException.class,
                () -> target.execute(new TestInput(conversation.id(), new MessageContent.Text("Olá"))));

        verifyNoInteractions(messagingGateway, messageGateway);
    }

    @Test
    void givenClosedWindow_whenTemplate_shouldSend() {
        final var conversation = Fixture.Conversations.of(Fixture.Customers.maria());
        when(conversationGateway.conversationOfId(conversation.id())).thenReturn(Optional.of(conversation));
        when(messageGateway.nextId()).thenReturn(new MessageId("msg-1"));
        when(messagingGateway.send(any(), any())).thenReturn(new SendResult.Accepted("wamid.tpl"));

        final var out = target.execute(new TestInput(conversation.id(), new MessageContent.Template("hello_world", "en_US", List.of())));

        Assertions.assertEquals("wamid.tpl", out.wamid());
    }
}
