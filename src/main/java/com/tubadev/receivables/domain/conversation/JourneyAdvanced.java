package com.tubadev.receivables.domain.conversation;

import com.tubadev.receivables.domain.utils.InstantUtils;

import java.time.Instant;

public record JourneyAdvanced(String conversationId, String from, String to, Instant occurredOn) implements ConversationEvent {

    public JourneyAdvanced {
        this.assertArgumentNotEmpty(conversationId, "'conversationId' should not be empty");
        this.assertArgumentNotEmpty(from, "'from' should not be empty");
        this.assertArgumentNotEmpty(to, "'to' should not be empty");
        this.assertArgumentNotNull(occurredOn, "'occurredOn' should not be null");
    }

    public JourneyAdvanced(final Conversation aConversation, final String from) {
        this(aConversation.id().value(), from, aConversation.stage().value(), InstantUtils.now());
    }
}
