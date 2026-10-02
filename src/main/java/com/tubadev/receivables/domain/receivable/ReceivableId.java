package com.tubadev.receivables.domain.receivable;

import com.tubadev.receivables.domain.Identifier;

public record ReceivableId(String value) implements Identifier<String> {

    public ReceivableId {
        this.assertArgumentNotEmpty(value, "'receivableId' should not be empty");
    }
}
