package com.tubadev.receivables.application.campaign.impl;

import com.tubadev.receivables.application.UseCaseTest;
import com.tubadev.receivables.application.campaign.DispatchCampaign;
import com.tubadev.receivables.application.message.SendMessage;
import com.tubadev.receivables.domain.Fixture;
import com.tubadev.receivables.domain.campaign.Campaign;
import com.tubadev.receivables.domain.campaign.CampaignCommand;
import com.tubadev.receivables.domain.campaign.CampaignGateway;
import com.tubadev.receivables.domain.campaign.DispatchStats;
import com.tubadev.receivables.domain.campaign.status.CampaignStatus;
import com.tubadev.receivables.domain.conversation.ConversationGateway;
import com.tubadev.receivables.domain.conversation.ConversationId;
import com.tubadev.receivables.domain.customer.CustomerGateway;
import com.tubadev.receivables.domain.customer.CustomerId;
import com.tubadev.receivables.domain.message.MessageContent;
import com.tubadev.receivables.domain.message.MessageId;
import com.tubadev.receivables.domain.message.status.MessageStatus;
import com.tubadev.receivables.domain.receivable.Eligibility;
import com.tubadev.receivables.domain.receivable.EligibilityGateway;
import com.tubadev.receivables.domain.receivable.Money;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.time.Clock;
import java.util.Optional;

import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DefaultDispatchCampaignTest extends UseCaseTest {

    @Mock
    private CampaignGateway campaignGateway;

    @Mock
    private ConversationGateway conversationGateway;

    @Mock
    private CustomerGateway customerGateway;

    @Mock
    private EligibilityGateway eligibilityGateway;

    @Mock
    private SendMessage sendMessage;

    @Captor
    private ArgumentCaptor<SendMessage.Input> sendCaptor;

    private DefaultDispatchCampaign target;

    record TestInput(String campaignId) implements DispatchCampaign.Input {}

    record TestOutput(MessageId messageId, String wamid, String status, String failureReason) implements SendMessage.Output {}

    @BeforeEach
    void setUp() {
        target = new DefaultDispatchCampaign(campaignGateway, Clock.systemUTC(), conversationGateway, customerGateway,
                eligibilityGateway, sendMessage, 4);
    }

    @Test
    void givenRunningCampaign_shouldSendToEligibleOptedInCustomersAndComplete() {
        final var maria = Fixture.Customers.maria();
        final var noOptIn = Fixture.Customers.notOptedIn();
        final var unknown = new CustomerId("cus_unknown");
        final var aCampaign = Fixture.Campaigns.anticipationOffer(maria.id(), noOptIn.id(), unknown);
        aCampaign.execute(new CampaignCommand.StartCampaign());

        when(campaignGateway.campaignOfId(aCampaign.id())).thenReturn(Optional.of(aCampaign));
        when(campaignGateway.save(any())).thenAnswer(returnsFirstArg());
        when(customerGateway.customerOfId(maria.id())).thenReturn(Optional.of(maria));
        when(customerGateway.customerOfId(noOptIn.id())).thenReturn(Optional.of(noOptIn));
        when(customerGateway.customerOfId(unknown)).thenReturn(Optional.empty());
        when(eligibilityGateway.eligibilityOf(maria.id())).thenReturn(Eligibility.of(Money.brl("20000"), 5));
        when(conversationGateway.openConversationOf(maria.phoneNumber())).thenReturn(Optional.empty());
        when(conversationGateway.nextId()).thenReturn(new ConversationId("conv-1"));
        when(sendMessage.execute(any())).thenReturn(new TestOutput(new MessageId("m1"), "wamid.1", MessageStatus.ACCEPTED, null));

        target.execute(new TestInput(aCampaign.id().value()));

        Assertions.assertEquals(CampaignStatus.COMPLETED, aCampaign.status().value());
        Assertions.assertEquals(new DispatchStats(1, 0, 2), aCampaign.stats());

        verify(sendMessage).execute(sendCaptor.capture());
        final var template = (MessageContent.Template) sendCaptor.getValue().content();
        Assertions.assertEquals("antecipacao_disponivel", template.name());
        Assertions.assertEquals("Maria", template.bodyParameters().getFirst());
        verify(conversationGateway).save(argThat(c -> aCampaign.id().equals(c.campaignId())));
    }

    @Test
    void givenRejectedSend_shouldCountAsFailed() {
        final var maria = Fixture.Customers.maria();
        final var aCampaign = Fixture.Campaigns.anticipationOffer(maria.id());
        aCampaign.execute(new CampaignCommand.StartCampaign());

        when(campaignGateway.campaignOfId(aCampaign.id())).thenReturn(Optional.of(aCampaign));
        when(customerGateway.customerOfId(maria.id())).thenReturn(Optional.of(maria));
        when(eligibilityGateway.eligibilityOf(maria.id())).thenReturn(Eligibility.of(Money.brl("20000"), 5));
        when(conversationGateway.openConversationOf(any())).thenReturn(Optional.empty());
        when(conversationGateway.nextId()).thenReturn(new ConversationId("conv-1"));
        when(sendMessage.execute(any())).thenReturn(new TestOutput(new MessageId("m1"), null, MessageStatus.FAILED, "[131030] not in allowed list"));

        target.execute(new TestInput(aCampaign.id().value()));

        Assertions.assertEquals(new DispatchStats(0, 1, 0), aCampaign.stats());
    }

    @Test
    void givenCustomerTalkingToUs_shouldNotInterruptTheJourney() {
        final var maria = Fixture.Customers.maria();
        final var aCampaign = Fixture.Campaigns.anticipationOffer(maria.id());
        aCampaign.execute(new CampaignCommand.StartCampaign());
        final var active = Fixture.Conversations.withInbound(maria, java.time.Instant.now());

        when(campaignGateway.campaignOfId(aCampaign.id())).thenReturn(Optional.of(aCampaign));
        when(customerGateway.customerOfId(maria.id())).thenReturn(Optional.of(maria));
        when(eligibilityGateway.eligibilityOf(maria.id())).thenReturn(Eligibility.of(Money.brl("20000"), 5));
        when(conversationGateway.openConversationOf(any())).thenReturn(Optional.of(active));

        target.execute(new TestInput(aCampaign.id().value()));

        Assertions.assertEquals(new DispatchStats(0, 0, 1), aCampaign.stats());
        verifyNoInteractions(sendMessage);
    }

    @Test
    void givenDraftCampaign_shouldDoNothing() {
        final Campaign aCampaign = Fixture.Campaigns.anticipationOffer(new CustomerId("c1"));
        when(campaignGateway.campaignOfId(aCampaign.id())).thenReturn(Optional.of(aCampaign));

        target.execute(new TestInput(aCampaign.id().value()));

        verify(campaignGateway, never()).save(any());
        verifyNoInteractions(customerGateway, sendMessage);
    }
}
