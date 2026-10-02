package com.tubadev.receivables.domain.validation;

import java.util.List;

public interface ValidationHandler {

    ValidationHandler append(Error anError);

    ValidationHandler append(ValidationHandler anHandler);

    <T> T validate(Validation<T> aValidation);

    List<Error> getErrors();

    default boolean hasError() {
        return getErrors() != null && !getErrors().isEmpty();
    }

    default Error firstError() {
        return hasError() ? getErrors().getFirst() : null;
    }

    @FunctionalInterface
    interface Validation<T> {
        T validate();
    }
}
