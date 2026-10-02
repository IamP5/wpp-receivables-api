package com.tubadev.receivables.domain.message;

import com.tubadev.receivables.domain.UnitTest;
import com.tubadev.receivables.domain.conversation.ConversationId;
import com.tubadev.receivables.domain.exceptions.DomainException;
import com.tubadev.receivables.domain.message.MessageCommand.*;
import com.tubadev.receivables.domain.message.status.MessageStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

class MessageTest extends UnitTest {

    private static Message outbound() {
        return Message.newOutbound(new MessageId("msg-1"), new ConversationId("conv-1"), new MessageContent.Text("Olá"));
    }

    @Test
    void givenOutbound_whenAcceptedAndDelivered_shouldFollowLifecycle() {
        final var aMessage = outbound();

        aMessage.execute(new MarkAccepted("wamid.1"));
        aMessage.execute(new MarkSent(), new MarkDelivered(), new MarkRead());

        Assertions.assertEquals("wamid.1", aMessage.wamid());
        Assertions.assertEquals(MessageStatus.READ, aMessage.status().value());
        Assertions.assertEquals(4, aMessage.domainEvents().size());
    }

    @Test
    void givenReadMessage_whenLateDeliveredArrives_shouldNotRegress() {
        final var aMessage = outbound();
        aMessage.execute(new MarkAccepted("wamid.1"), new MarkRead());

        aMessage.execute(new MarkDelivered(), new MarkSent());

        Assertions.assertEquals(MessageStatus.READ, aMessage.status().value());
    }

    @Test
    void givenSentMessage_whenFails_shouldKeepReasonAndRegisterEvent() {
        final var aMessage = outbound();
        aMessage.execute(new MarkAccepted("wamid.1"), new MarkSent());

        aMessage.execute(new MarkFailed("[131047] Re-engagement message"));

        Assertions.assertTrue(aMessage.isFailed());
        Assertions.assertEquals("[131047] Re-engagement message", aMessage.failureReason());
        Assertions.assertInstanceOf(MessageFailed.class, aMessage.domainEvents().getLast());
    }

    @Test
    void givenInbound_whenChangingStatus_shouldThrow() {
        final var aMessage = Message.newInbound(new MessageId("msg-2"), new ConversationId("conv-1"), "wamid.in",
                new MessageContent.Text("oi"));

        Assertions.assertInstanceOf(MessageReceived.class, aMessage.domainEvents().getFirst());
        Assertions.assertThrows(DomainException.class, () -> aMessage.execute(new MarkRead()));
    }

    @Test
    void givenMoreThanThreeButtons_whenCreating_shouldThrow() {
        final var b = new MessageContent.Buttons.Button("A", "A");
        Assertions.assertThrows(DomainException.class, () -> new MessageContent.Buttons("x", List.of(b, b, b, b)));
    }

    @Test
    void givenTemplate_shouldNotRequireServiceWindow() {
        Assertions.assertFalse(new MessageContent.Template("hello_world", "en_US", null).requiresServiceWindow());
        Assertions.assertTrue(new MessageContent.Text("x").requiresServiceWindow());
    }
}
