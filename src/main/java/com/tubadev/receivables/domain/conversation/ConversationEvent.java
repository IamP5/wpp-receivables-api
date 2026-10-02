package com.tubadev.receivables.domain.conversation;

import com.tubadev.receivables.domain.DomainEvent;

public sealed interface ConversationEvent extends DomainEvent
        permits ConversationStarted, JourneyAdvanced, ConversationEnded {

    String TYPE = "Conversation";

    String conversationId();

    @Override
    default String aggregateId() {
        return conversationId();
    }

    @Override
    default String aggregateType() {
        return TYPE;
    }
}
