package com.tubadev.receivables.application.conversation.impl;

import com.tubadev.receivables.application.UseCaseTest;
import com.tubadev.receivables.application.conversation.HandleInboundMessage;
import com.tubadev.receivables.application.journey.AnticipationJourney;
import com.tubadev.receivables.application.message.SendMessage;
import com.tubadev.receivables.domain.Fixture;
import com.tubadev.receivables.domain.conversation.Conversation;
import com.tubadev.receivables.domain.conversation.ConversationGateway;
import com.tubadev.receivables.domain.conversation.ConversationCommand.AdvanceTo;
import com.tubadev.receivables.domain.conversation.ConversationId;
import com.tubadev.receivables.domain.conversation.SessionPolicy;
import com.tubadev.receivables.domain.conversation.journey.Intent;
import com.tubadev.receivables.domain.conversation.journey.JourneyStage;
import com.tubadev.receivables.domain.customer.CustomerGateway;
import com.tubadev.receivables.domain.message.Message;
import com.tubadev.receivables.domain.message.MessageCommand.MarkAccepted;
import com.tubadev.receivables.domain.message.MessageContent;
import com.tubadev.receivables.domain.message.MessageContent.Buttons;
import com.tubadev.receivables.domain.message.MessageContent.Buttons.Button;
import com.tubadev.receivables.domain.message.MessageGateway;
import com.tubadev.receivables.domain.message.MessageId;
import com.tubadev.receivables.domain.receivable.Money;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DefaultHandleInboundMessageTest extends UseCaseTest {

    private static final SessionPolicy POLICY = new SessionPolicy(Duration.ofMinutes(30), Duration.ofHours(2));

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

    private DefaultHandleInboundMessage target;

    @Captor
    private ArgumentCaptor<Conversation> conversationCaptor;

    @Captor
    private ArgumentCaptor<Message> messageCaptor;

    record TestInput(String wamid, String from, MessageContent content, Instant receivedAt, String replyTo)
            implements HandleInboundMessage.Input {

        TestInput(final String wamid, final String from, final MessageContent content, final Instant receivedAt) {
            this(wamid, from, content, receivedAt, null);
        }
    }

    @BeforeEach
    void setUp() {
        target = new DefaultHandleInboundMessage(anticipationJourney, conversationGateway, customerGateway, messageGateway, sendMessage,
                POLICY);
    }

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

    @Test
    void givenIdleSession_whenCustomerWritesAgain_shouldCloseItAndStartANewOne() {
        final var maria = Fixture.Customers.maria();
        final var now = Instant.now();
        final var idle = Fixture.Conversations.withInbound(maria, now.minus(Duration.ofMinutes(31)));
        idle.execute(new AdvanceTo(new JourneyStage.AwaitingAmount(Money.brl("15000"))));
        givenInboundFrom(maria);
        when(conversationGateway.openConversationOf(maria.phoneNumber())).thenReturn(Optional.of(idle));
        when(conversationGateway.nextId()).thenReturn(new ConversationId("conv-new"));

        final var out = target.execute(new TestInput("wamid.2", "5511988887777", new MessageContent.Text("oi"), now));

        Assertions.assertEquals(new ConversationId("conv-new"), out.conversationId());
        Assertions.assertEquals(new JourneyStage.Closed(JourneyStage.Closed.INACTIVITY), idle.stage());
        verify(conversationGateway).save(idle);
    }

    @Test
    void givenActiveSession_whenCustomerWrites_shouldKeepIt() {
        final var maria = Fixture.Customers.maria();
        final var now = Instant.now();
        final var active = Fixture.Conversations.withInbound(maria, now.minus(Duration.ofMinutes(29)));
        givenInboundFrom(maria);
        when(conversationGateway.openConversationOf(maria.phoneNumber())).thenReturn(Optional.of(active));

        final var out = target.execute(new TestInput("wamid.2", "5511988887777", new MessageContent.Text("oi"), now));

        Assertions.assertEquals(active.id(), out.conversationId());
        Assertions.assertTrue(active.isOpen());
        verify(conversationGateway, never()).nextId();
    }

    @Test
    void givenConfirmTappedOnMessageOfAnEarlierSession_shouldBeStale() {
        final var maria = Fixture.Customers.maria();
        final var current = Fixture.Conversations.withInbound(maria, Instant.now());
        givenInboundFrom(maria);
        when(conversationGateway.openConversationOf(maria.phoneNumber())).thenReturn(Optional.of(current));
        when(messageGateway.messageOfWamid("wamid.offer")).thenReturn(Optional.of(offerButtons(new ConversationId("conv-old"), "wamid.offer")));

        target.execute(confirmTappedOn("wamid.offer"));

        verify(anticipationJourney).handle(any(), any(), eq(new Intent.StaleReply(new Intent.ConfirmOffer())));
    }

    @Test
    void givenConfirmTappedOnAReplacedOffer_shouldBeStale() {
        final var maria = Fixture.Customers.maria();
        final var current = Fixture.Conversations.withInbound(maria, Instant.now());
        final var replaced = offerButtons(current.id(), "wamid.offer-1");
        givenInboundFrom(maria);
        when(conversationGateway.openConversationOf(maria.phoneNumber())).thenReturn(Optional.of(current));
        when(messageGateway.messageOfWamid("wamid.offer-1")).thenReturn(Optional.of(replaced));
        when(messageGateway.messagesOfConversation(current.id())).thenReturn(List.of(replaced, offerButtons(current.id(), "wamid.offer-2")));

        target.execute(confirmTappedOn("wamid.offer-1"));

        verify(anticipationJourney).handle(any(), any(), eq(new Intent.StaleReply(new Intent.ConfirmOffer())));
    }

    @Test
    void givenConfirmTappedOnTheLatestOffer_shouldConfirm() {
        final var maria = Fixture.Customers.maria();
        final var current = Fixture.Conversations.withInbound(maria, Instant.now());
        final var latest = offerButtons(current.id(), "wamid.offer-2");
        givenInboundFrom(maria);
        when(conversationGateway.openConversationOf(maria.phoneNumber())).thenReturn(Optional.of(current));
        when(messageGateway.messageOfWamid("wamid.offer-2")).thenReturn(Optional.of(latest));
        when(messageGateway.messagesOfConversation(current.id())).thenReturn(List.of(offerButtons(current.id(), "wamid.offer-1"), latest));

        target.execute(confirmTappedOn("wamid.offer-2"));

        verify(anticipationJourney).handle(any(), any(), eq(new Intent.ConfirmOffer()));
    }

    @Test
    void givenAnticipateTappedOnAnOldCampaign_shouldStillCount() {
        final var maria = Fixture.Customers.maria();
        givenInboundFrom(maria);
        when(conversationGateway.openConversationOf(maria.phoneNumber())).thenReturn(Optional.empty());
        when(conversationGateway.nextId()).thenReturn(new ConversationId("conv-new"));

        target.execute(new TestInput("wamid.tap", "5511988887777", new MessageContent.Reply(Intent.ANTICIPATE, "Antecipar"),
                Instant.now(), "wamid.campaign"));

        verify(anticipationJourney).handle(any(), any(), eq(new Intent.WantsAnticipation()));
        verify(messageGateway, never()).messageOfWamid("wamid.campaign");
    }

    private void givenInboundFrom(final com.tubadev.receivables.domain.customer.Customer customer) {
        when(messageGateway.messageOfWamid(argThat(wamid -> wamid.startsWith("wamid.") && !wamid.startsWith("wamid.offer"))))
                .thenReturn(Optional.empty());
        when(customerGateway.customerOfPhoneNumber(customer.phoneNumber())).thenReturn(Optional.of(customer));
        when(messageGateway.nextId()).thenReturn(new MessageId("msg-in"));
        when(messageGateway.save(any())).thenAnswer(returnsFirstArg());
        when(conversationGateway.save(any())).thenAnswer(returnsFirstArg());
        when(anticipationJourney.handle(any(), any(), any())).thenReturn(List.of());
    }

    private static TestInput confirmTappedOn(final String wamid) {
        return new TestInput("wamid.tap", "5511988887777", new MessageContent.Reply(Intent.CONFIRM_OFFER, "Confirmar"), Instant.now(),
                wamid);
    }

    private static Message offerButtons(final ConversationId conversationId, final String wamid) {
        final var offer = Message.newOutbound(new MessageId(wamid), conversationId, new Buttons("Confirma a antecipação?",
                List.of(new Button(Intent.CONFIRM_OFFER, "Confirmar"), new Button(Intent.DECLINE_OFFER, "Cancelar"))));
        offer.execute(new MarkAccepted(wamid));
        return offer;
    }
}
