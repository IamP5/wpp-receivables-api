package com.tubadev.receivables.application.conversation.impl;

import com.tubadev.receivables.application.conversation.ReleaseConversation;
import com.tubadev.receivables.application.journey.JourneyReplies;
import com.tubadev.receivables.application.message.SendMessage;
import com.tubadev.receivables.domain.conversation.Conversation;
import com.tubadev.receivables.domain.conversation.ConversationCommand.Release;
import com.tubadev.receivables.domain.conversation.ConversationGateway;
import com.tubadev.receivables.domain.conversation.ConversationId;
import com.tubadev.receivables.domain.exceptions.DomainException;
import com.tubadev.receivables.domain.message.MessageContent;
import com.tubadev.receivables.domain.message.status.MessageStatus;

import java.time.Clock;
import java.util.Objects;

public class DefaultReleaseConversation extends ReleaseConversation {

    private final Clock clock;
    private final ConversationGateway conversationGateway;
    private final SendMessage sendMessage;

    public DefaultReleaseConversation(final Clock clock, final ConversationGateway conversationGateway, final SendMessage sendMessage) {
        this.clock = Objects.requireNonNull(clock);
        this.conversationGateway = Objects.requireNonNull(conversationGateway);
        this.sendMessage = Objects.requireNonNull(sendMessage);
    }

    @Override
    public Output execute(final Input in) {
        if (in == null) {
            throw new IllegalArgumentException("Input of DefaultReleaseConversation should not be null");
        }

        final var conversationId = new ConversationId(in.conversationId());
        final var aConversation = this.conversationGateway.conversationOfId(conversationId)
                .orElseThrow(() -> DomainException.notFound(Conversation.class, conversationId));

        aConversation.execute(new Release());
        this.conversationGateway.save(aConversation);

        var notified = false;
        if (aConversation.isWithinServiceWindow(clock.instant())) {
            final var out = this.sendMessage.execute(new ReleaseNoticeInput(conversationId, JourneyReplies.handoffReleased()));
            notified = !MessageStatus.FAILED.equals(out.status());
        }

        return new StdOutput(conversationId, aConversation.stage().value(), notified);
    }

    record ReleaseNoticeInput(ConversationId conversationId, MessageContent content) implements SendMessage.Input {
    }

    record StdOutput(ConversationId conversationId, String stage, boolean customerNotified) implements Output {
    }
}
