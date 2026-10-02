package com.tubadev.receivables.domain.exceptions;

import com.tubadev.receivables.domain.AggregateRoot;
import com.tubadev.receivables.domain.Identifier;
import com.tubadev.receivables.domain.validation.Error;

import java.util.List;

public class DomainException extends NoStacktraceException {

    protected final List<Error> errors;

    protected DomainException(final String aMessage, final List<Error> anErrors) {
        super(aMessage);
        this.errors = anErrors;
    }

    public static DomainException with(final String aMessage) {
        return new DomainException(aMessage, List.of(new Error(aMessage)));
    }

    public static DomainException with(final Error anError) {
        return new DomainException(anError.message(), List.of(anError));
    }

    public static DomainException with(final List<Error> anErrors) {
        return new DomainException("", anErrors);
    }

    public static DomainException notFound(final Class<? extends AggregateRoot<?>> aggClass, final Identifier<?> id) {
        return NotFoundException.with("%s with id %s was not found".formatted(aggClass.getSimpleName(), id.value()));
    }

    public List<Error> getErrors() {
        return errors;
    }
}
