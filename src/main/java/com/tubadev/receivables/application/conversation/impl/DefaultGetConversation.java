package com.tubadev.receivables.application.conversation.impl;

import com.tubadev.receivables.application.conversation.GetConversation;
import com.tubadev.receivables.domain.conversation.Conversation;
import com.tubadev.receivables.domain.conversation.ConversationGateway;
import com.tubadev.receivables.domain.conversation.ConversationId;
import com.tubadev.receivables.domain.exceptions.DomainException;
import com.tubadev.receivables.domain.message.Message;
import com.tubadev.receivables.domain.message.MessageGateway;

import java.util.List;
import java.util.Objects;

public class DefaultGetConversation extends GetConversation {

    private final ConversationGateway conversationGateway;
    private final MessageGateway messageGateway;

    public DefaultGetConversation(final ConversationGateway conversationGateway, final MessageGateway messageGateway) {
        this.conversationGateway = Objects.requireNonNull(conversationGateway);
        this.messageGateway = Objects.requireNonNull(messageGateway);
    }

    @Override
    public Output execute(final Input in) {
        final var conversationId = new ConversationId(in.conversationId());
        final var aConversation = this.conversationGateway.conversationOfId(conversationId)
                .orElseThrow(() -> DomainException.notFound(Conversation.class, conversationId));
        return new StdOutput(aConversation, this.messageGateway.messagesOfConversation(conversationId));
    }

    record StdOutput(Conversation conversation, List<Message> messages) implements Output {
    }
}
