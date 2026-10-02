package com.tubadev.receivables.infrastructure.configuration.usecases;

import com.tubadev.receivables.application.contract.IssueContract;
import com.tubadev.receivables.application.conversation.GetConversation;
import com.tubadev.receivables.application.conversation.HandleInboundMessage;
import com.tubadev.receivables.application.conversation.ReleaseConversation;
import com.tubadev.receivables.application.conversation.SendAgentMessage;
import com.tubadev.receivables.application.conversation.impl.DefaultGetConversation;
import com.tubadev.receivables.application.conversation.impl.DefaultHandleInboundMessage;
import com.tubadev.receivables.application.conversation.impl.DefaultReleaseConversation;
import com.tubadev.receivables.application.conversation.impl.DefaultSendAgentMessage;
import com.tubadev.receivables.application.journey.AnticipationJourney;
import com.tubadev.receivables.application.journey.impl.DefaultAnticipationJourney;
import com.tubadev.receivables.application.message.SendMessage;
import com.tubadev.receivables.domain.biometrics.BiometricsGateway;
import com.tubadev.receivables.domain.conversation.ConversationGateway;
import com.tubadev.receivables.domain.conversation.SessionPolicy;
import com.tubadev.receivables.domain.customer.CustomerGateway;
import com.tubadev.receivables.domain.message.MediaGateway;
import com.tubadev.receivables.domain.message.MessageGateway;
import com.tubadev.receivables.domain.receivable.AnticipationGateway;
import com.tubadev.receivables.domain.receivable.EligibilityGateway;
import com.tubadev.receivables.infrastructure.configuration.properties.JourneyProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ConversationUseCaseConfig {

    @Bean
    SessionPolicy sessionPolicy(final JourneyProperties properties) {
        return new SessionPolicy(properties.sessionTimeout(), properties.handoffTimeout());
    }

    @Bean
    AnticipationJourney anticipationJourney(
            final AnticipationGateway anticipationGateway,
            final BiometricsGateway biometricsGateway,
            final Clock clock,
            final CustomerGateway customerGateway,
            final EligibilityGateway eligibilityGateway,
            final MediaGateway mediaGateway,
            final IssueContract issueContract
    ) {
        return new DefaultAnticipationJourney(anticipationGateway, biometricsGateway, clock, customerGateway, eligibilityGateway,
                mediaGateway, issueContract);
    }

    @Bean
    HandleInboundMessage handleInboundMessage(
            final AnticipationJourney anticipationJourney,
            final ConversationGateway conversationGateway,
            final CustomerGateway customerGateway,
            final MessageGateway messageGateway,
            final SendMessage sendMessage,
            final SessionPolicy sessionPolicy
    ) {
        return new DefaultHandleInboundMessage(anticipationJourney, conversationGateway, customerGateway, messageGateway, sendMessage,
                sessionPolicy);
    }

    @Bean
    ReleaseConversation releaseConversation(final Clock clock, final ConversationGateway conversationGateway, final SendMessage sendMessage) {
        return new DefaultReleaseConversation(clock, conversationGateway, sendMessage);
    }

    @Bean
    SendAgentMessage sendAgentMessage(final Clock clock, final ConversationGateway conversationGateway, final SendMessage sendMessage) {
        return new DefaultSendAgentMessage(clock, conversationGateway, sendMessage);
    }

    @Bean
    GetConversation getConversation(final ConversationGateway conversationGateway, final MessageGateway messageGateway) {
        return new DefaultGetConversation(conversationGateway, messageGateway);
    }
}
