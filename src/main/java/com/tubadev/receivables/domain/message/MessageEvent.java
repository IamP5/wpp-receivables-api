package com.tubadev.receivables.domain.message;

import com.tubadev.receivables.domain.DomainEvent;

public sealed interface MessageEvent extends DomainEvent permits MessageReceived, MessageStatusChanged, MessageFailed {

    String TYPE = "Message";

    String messageId();

    @Override
    default String aggregateId() {
        return messageId();
    }

    @Override
    default String aggregateType() {
        return TYPE;
    }
}
