package com.tubadev.receivables.domain.exceptions;

import com.tubadev.receivables.domain.validation.Error;

import java.util.List;

public class NotFoundException extends DomainException {

    protected NotFoundException(final String aMessage) {
        super(aMessage, List.of(new Error(aMessage)));
    }

    public static NotFoundException with(final String aMessage) {
        return new NotFoundException(aMessage);
    }
}
