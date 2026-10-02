package com.tubadev.receivables.domain.conversation;

import com.tubadev.receivables.domain.Identifier;
import com.tubadev.receivables.domain.utils.InstantUtils;

import java.time.Instant;

public record ConversationStarted(String conversationId, String phoneNumber, String customerId, String campaignId, Instant occurredOn)
        implements ConversationEvent {

    public ConversationStarted {
        this.assertArgumentNotEmpty(conversationId, "'conversationId' should not be empty");
        this.assertArgumentNotEmpty(phoneNumber, "'phoneNumber' should not be empty");
        this.assertArgumentNotNull(occurredOn, "'occurredOn' should not be null");
    }

    public ConversationStarted(final Conversation aConversation) {
        this(
                aConversation.id().value(),
                aConversation.phoneNumber().value(),
                valueOf(aConversation.customerId()),
                valueOf(aConversation.campaignId()),
                InstantUtils.now()
        );
    }

    private static String valueOf(final Identifier<String> id) {
        return id == null ? null : id.value();
    }
}
