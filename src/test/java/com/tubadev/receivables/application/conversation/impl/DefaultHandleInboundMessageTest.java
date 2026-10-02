package com.tubadev.receivables.application.conversation.impl;

import com.tubadev.receivables.application.UseCaseTest;
import com.tubadev.receivables.application.conversation.HandleInboundMessage;
import com.tubadev.receivables.application.journey.AnticipationJourney;
import com.tubadev.receivables.application.message.SendMessage;
import com.tubadev.receivables.domain.Fixture;
import com.tubadev.receivables.domain.conversation.Conversation;
import com.tubadev.receivables.domain.conversation.ConversationGateway;
import com.tubadev.receivables.domain.conversation.ConversationId;
import com.tubadev.receivables.domain.conversation.journey.Intent;
import com.tubadev.receivables.domain.customer.CustomerGateway;
import com.tubadev.receivables.domain.message.Message;
import com.tubadev.receivables.domain.message.MessageContent;
import com.tubadev.receivables.domain.message.MessageGateway;
import com.tubadev.receivables.domain.message.MessageId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DefaultHandleInboundMessageTest extends UseCaseTest {

    @Mock
    private AnticipationJourney anticipationJourney;

    @Mock
    private ConversationGateway conversationGateway;

    @Mock
    private CustomerGateway customerGateway;

    @Mock
    private MessageGateway messageGateway;

    @Mock
    private SendMessage sendMessage;

    @InjectMocks
    private DefaultHandleInboundMessage target;

    @Captor
    private ArgumentCaptor<Conversation> conversationCaptor;

    @Captor
    private ArgumentCaptor<Message> messageCaptor;

    record TestInput(String wamid, String from, MessageContent content, Instant receivedAt) implements HandleInboundMessage.Input {}

    @Test
    void givenFirstMessage_shouldOpenConversationStoreInboundAndSendReplies() {
        final var maria = Fixture.Customers.maria();
        final var receivedAt = Instant.now();
        final var reply = new MessageContent.Text("Olá!");

        when(messageGateway.messageOfWamid("wamid.1")).thenReturn(Optional.empty());
        when(customerGateway.customerOfPhoneNumber(maria.phoneNumber())).thenReturn(Optional.of(maria));
        when(conversationGateway.openConversationOf(maria.phoneNumber())).thenReturn(Optional.empty());
        when(conversationGateway.nextId()).thenReturn(new ConversationId("conv-1"));
        when(messageGateway.nextId()).thenReturn(new MessageId("msg-1"));
        when(messageGateway.save(any())).thenAnswer(returnsFirstArg());
        when(conversationGateway.save(any())).thenAnswer(returnsFirstArg());
        when(anticipationJourney.handle(any(), eq(Optional.of(maria)), eq(new Intent.Greeting()))).thenReturn(List.of(reply));

        final var out = target.execute(new TestInput("wamid.1", "+55 11 98888-7777", new MessageContent.Text("oi"), receivedAt));

        Assertions.assertFalse(out.duplicated());
        Assertions.assertEquals(new ConversationId("conv-1"), out.conversationId());
        Assertions.assertEquals(1, out.repliesSent());

        verify(conversationGateway, times(2)).save(conversationCaptor.capture());
        Assertions.assertEquals(receivedAt, conversationCaptor.getValue().lastInboundAt());
        Assertions.assertEquals(maria.id(), conversationCaptor.getValue().customerId());

        verify(messageGateway).save(messageCaptor.capture());
        Assertions.assertTrue(messageCaptor.getValue().isInbound());
        Assertions.assertEquals("wamid.1", messageCaptor.getValue().wamid());

        verify(sendMessage).execute(argThat((SendMessage.Input in) -> in.content().equals(reply)));
    }

    @Test
    void givenRedeliveredWebhook_shouldNotProcessTwice() {
        final var existing = Message.newInbound(new MessageId("msg-1"), new ConversationId("conv-1"), "wamid.1",
                new MessageContent.Text("oi"));
        when(messageGateway.messageOfWamid("wamid.1")).thenReturn(Optional.of(existing));

        final var out = target.execute(new TestInput("wamid.1", "5511988887777", new MessageContent.Text("oi"), Instant.now()));

        Assertions.assertTrue(out.duplicated());
        verifyNoInteractions(anticipationJourney, sendMessage, conversationGateway, customerGateway);
    }
}
