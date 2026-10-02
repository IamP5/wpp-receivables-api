package com.tubadev.receivables.application.message.impl;

import com.tubadev.receivables.application.message.SendMessage;
import com.tubadev.receivables.domain.conversation.Conversation;
import com.tubadev.receivables.domain.conversation.ConversationGateway;
import com.tubadev.receivables.domain.exceptions.DomainException;
import com.tubadev.receivables.domain.message.Message;
import com.tubadev.receivables.domain.message.MessageCommand.MarkAccepted;
import com.tubadev.receivables.domain.message.MessageCommand.MarkFailed;
import com.tubadev.receivables.domain.message.MessageGateway;
import com.tubadev.receivables.domain.message.MessageId;
import com.tubadev.receivables.domain.message.MessagingGateway;
import com.tubadev.receivables.domain.message.SendResult.Accepted;
import com.tubadev.receivables.domain.message.SendResult.Rejected;

import java.time.Clock;
import java.util.Objects;

public class DefaultSendMessage extends SendMessage {

    private final Clock clock;
    private final ConversationGateway conversationGateway;
    private final MessageGateway messageGateway;
    private final MessagingGateway messagingGateway;

    public DefaultSendMessage(
            final Clock clock,
            final ConversationGateway conversationGateway,
            final MessageGateway messageGateway,
            final MessagingGateway messagingGateway
    ) {
        this.clock = Objects.requireNonNull(clock);
        this.conversationGateway = Objects.requireNonNull(conversationGateway);
        this.messageGateway = Objects.requireNonNull(messageGateway);
        this.messagingGateway = Objects.requireNonNull(messagingGateway);
    }

    @Override
    public Output execute(final Input in) {
        if (in == null) {
            throw new IllegalArgumentException("Input of DefaultSendMessage should not be null");
        }

        final var aConversation = this.conversationGateway.conversationOfId(in.conversationId())
                .orElseThrow(() -> DomainException.notFound(Conversation.class, in.conversationId()));

        aConversation.assertCanSend(in.content(), clock.instant());

        final var aMessage = Message.newOutbound(this.messageGateway.nextId(), aConversation.id(), in.content());

        switch (this.messagingGateway.send(aConversation.phoneNumber(), in.content())) {
            case Accepted(var wamid) -> aMessage.execute(new MarkAccepted(wamid));
            case Rejected rejected -> aMessage.execute(new MarkFailed(rejected.describe()));
        }

        this.messageGateway.save(aMessage);
        return new StdOutput(aMessage.id(), aMessage.wamid(), aMessage.status().value(), aMessage.failureReason());
    }

    record StdOutput(MessageId messageId, String wamid, String status, String failureReason) implements Output {
    }
}
