package com.tubadev.receivables.domain.message;

import com.tubadev.receivables.domain.utils.InstantUtils;

import java.time.Instant;

public record MessageReceived(String messageId, String conversationId, String wamid, String contentType, Instant occurredOn)
        implements MessageEvent {

    public MessageReceived {
        this.assertArgumentNotEmpty(messageId, "'messageId' should not be empty");
        this.assertArgumentNotEmpty(conversationId, "'conversationId' should not be empty");
        this.assertArgumentNotEmpty(wamid, "'wamid' should not be empty");
        this.assertArgumentNotNull(occurredOn, "'occurredOn' should not be null");
    }

    public MessageReceived(final Message aMessage) {
        this(aMessage.id().value(), aMessage.conversationId().value(), aMessage.wamid(), aMessage.content().type(), InstantUtils.now());
    }
}
