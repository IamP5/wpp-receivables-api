package com.tubadev.receivables.domain.validation;

public record Error(String property, String message) {

    public Error(final String message) {
        this("", message);
    }
}
