package com.tubadev.receivables.domain.message;

import com.tubadev.receivables.domain.Identifier;

public record MessageId(String value) implements Identifier<String> {

    public MessageId {
        this.assertArgumentNotEmpty(value, "'messageId' should not be empty");
    }
}
