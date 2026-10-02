package com.tubadev.receivables.domain.message;

import com.tubadev.receivables.domain.utils.InstantUtils;

import java.time.Instant;

public record MessageStatusChanged(String messageId, String wamid, String from, String to, Instant occurredOn)
        implements MessageEvent {

    public MessageStatusChanged {
        this.assertArgumentNotEmpty(messageId, "'messageId' should not be empty");
        this.assertArgumentNotEmpty(from, "'from' should not be empty");
        this.assertArgumentNotEmpty(to, "'to' should not be empty");
        this.assertArgumentNotNull(occurredOn, "'occurredOn' should not be null");
    }

    public MessageStatusChanged(final Message aMessage, final String from) {
        this(aMessage.id().value(), aMessage.wamid(), from, aMessage.status().value(), InstantUtils.now());
    }
}
