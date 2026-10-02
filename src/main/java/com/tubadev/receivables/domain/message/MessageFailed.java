package com.tubadev.receivables.domain.message;

import com.tubadev.receivables.domain.utils.InstantUtils;

import java.time.Instant;

public record MessageFailed(String messageId, String conversationId, String wamid, String reason, Instant occurredOn)
        implements MessageEvent {

    public MessageFailed {
        this.assertArgumentNotEmpty(messageId, "'messageId' should not be empty");
        this.assertArgumentNotEmpty(conversationId, "'conversationId' should not be empty");
        this.assertArgumentNotEmpty(reason, "'reason' should not be empty");
        this.assertArgumentNotNull(occurredOn, "'occurredOn' should not be null");
    }

    public MessageFailed(final Message aMessage) {
        this(aMessage.id().value(), aMessage.conversationId().value(), aMessage.wamid(), aMessage.failureReason(), InstantUtils.now());
    }
}
