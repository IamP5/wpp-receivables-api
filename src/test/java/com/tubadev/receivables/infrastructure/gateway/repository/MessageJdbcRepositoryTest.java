package com.tubadev.receivables.infrastructure.gateway.repository;

import com.tubadev.receivables.AbstractRepositoryTest;
import com.tubadev.receivables.domain.Fixture;
import com.tubadev.receivables.domain.message.Message;
import com.tubadev.receivables.domain.message.MessageCommand.MarkAccepted;
import com.tubadev.receivables.domain.message.MessageCommand.MarkDelivered;
import com.tubadev.receivables.domain.message.MessageContent;
import com.tubadev.receivables.domain.message.MessageId;
import com.tubadev.receivables.domain.message.status.MessageStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

class MessageJdbcRepositoryTest extends AbstractRepositoryTest {

    @Test
    void givenOutboundButtons_whenSavedAndUpdated_shouldRoundTripContentAndStatus() {
        final var aConversation = Fixture.Conversations.of(Fixture.Customers.maria());
        conversationRepository().save(aConversation);

        final var content = new MessageContent.Buttons("Confirma?", List.of(
                new MessageContent.Buttons.Button("CONFIRM_OFFER", "Confirmar"),
                new MessageContent.Buttons.Button("DECLINE_OFFER", "Cancelar")));
        final var aMessage = Message.newOutbound(new MessageId("msg-out-1"), aConversation.id(), content);
        aMessage.execute(new MarkAccepted("wamid.out.1"));
        messageRepository().save(aMessage);

        final var byWamid = messageRepository().messageOfWamid("wamid.out.1").orElseThrow();
        Assertions.assertEquals(content, byWamid.content());
        Assertions.assertEquals(MessageStatus.ACCEPTED, byWamid.status().value());

        byWamid.execute(new MarkDelivered());
        messageRepository().save(byWamid);

        Assertions.assertEquals(MessageStatus.DELIVERED,
                messageRepository().messageOfId(aMessage.id()).orElseThrow().status().value());
    }

    @Test
    void givenConversationMessages_whenListing_shouldReturnInOrder() {
        final var aConversation = Fixture.Conversations.of(Fixture.Customers.maria());
        conversationRepository().save(aConversation);

        messageRepository().save(Message.newInbound(new MessageId("msg-in-1"), aConversation.id(), "wamid.in.1",
                new MessageContent.Text("oi")));
        messageRepository().save(Message.newOutbound(new MessageId("msg-out-2"), aConversation.id(), new MessageContent.Text("Olá!")));

        final var messages = messageRepository().messagesOfConversation(aConversation.id());

        Assertions.assertEquals(List.of("msg-in-1", "msg-out-2"), messages.stream().map(m -> m.id().value()).toList());
        Assertions.assertTrue(messages.getFirst().isInbound());
    }
}
