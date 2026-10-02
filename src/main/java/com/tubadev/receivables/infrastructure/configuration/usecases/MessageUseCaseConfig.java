package com.tubadev.receivables.infrastructure.configuration.usecases;

import com.tubadev.receivables.application.message.SendMessage;
import com.tubadev.receivables.application.message.UpdateMessageStatus;
import com.tubadev.receivables.application.message.impl.DefaultSendMessage;
import com.tubadev.receivables.application.message.impl.DefaultUpdateMessageStatus;
import com.tubadev.receivables.domain.conversation.ConversationGateway;
import com.tubadev.receivables.domain.message.MessageGateway;
import com.tubadev.receivables.domain.message.MessagingGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class MessageUseCaseConfig {

    @Bean
    SendMessage sendMessage(
            final Clock clock,
            final ConversationGateway conversationGateway,
            final MessageGateway messageGateway,
            final MessagingGateway messagingGateway
    ) {
        return new DefaultSendMessage(clock, conversationGateway, messageGateway, messagingGateway);
    }

    @Bean
    UpdateMessageStatus updateMessageStatus(final MessageGateway messageGateway) {
        return new DefaultUpdateMessageStatus(messageGateway);
    }
}
