package com.tubadev.receivables.domain.conversation;

import com.tubadev.receivables.domain.Identifier;

public record ConversationId(String value) implements Identifier<String> {

    public ConversationId {
        this.assertArgumentNotEmpty(value, "'conversationId' should not be empty");
    }
}
