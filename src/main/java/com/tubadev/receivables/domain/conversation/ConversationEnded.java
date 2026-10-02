package com.tubadev.receivables.domain.conversation;

import com.tubadev.receivables.domain.conversation.journey.JourneyStage.Closed;
import com.tubadev.receivables.domain.conversation.journey.JourneyStage.Completed;
import com.tubadev.receivables.domain.utils.InstantUtils;

import java.time.Instant;

/**
 * Journey outcome: {@code completed} carries the anticipation protocol, {@code closed} the reason.
 */
public record ConversationEnded(String conversationId, String customerId, String campaignId, String outcome, String detail, Instant occurredOn)
        implements ConversationEvent {

    public ConversationEnded {
        this.assertArgumentNotEmpty(conversationId, "'conversationId' should not be empty");
        this.assertArgumentNotEmpty(outcome, "'outcome' should not be empty");
        this.assertArgumentNotNull(occurredOn, "'occurredOn' should not be null");
    }

    public ConversationEnded(final Conversation aConversation) {
        this(
                aConversation.id().value(),
                aConversation.customerId() == null ? null : aConversation.customerId().value(),
                aConversation.campaignId() == null ? null : aConversation.campaignId().value(),
                aConversation.stage().value(),
                switch (aConversation.stage()) {
                    case Completed(var protocol) -> protocol;
                    case Closed(var reason) -> reason;
                    default -> null;
                },
                InstantUtils.now()
        );
    }
}
