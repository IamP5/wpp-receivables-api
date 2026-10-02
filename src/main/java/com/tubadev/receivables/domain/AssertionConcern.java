package com.tubadev.receivables.domain;

import com.tubadev.receivables.domain.exceptions.DomainException;

import java.util.Collection;

public interface AssertionConcern {

    default <T> T assertArgumentNotNull(final T val, final String aMessage) {
        if (val == null) {
            throw DomainException.with(aMessage);
        }
        return val;
    }

    default String assertArgumentNotEmpty(final String val, final String aMessage) {
        if (val == null || val.isBlank()) {
            throw DomainException.with(aMessage);
        }
        return val;
    }

    default <C extends Collection<?>> C assertArgumentNotEmpty(final C val, final String aMessage) {
        if (val == null || val.isEmpty()) {
            throw DomainException.with(aMessage);
        }
        return val;
    }

    default String assertArgumentLength(final String val, final int length, final String aMessage) {
        if (val == null || val.length() != length) {
            throw DomainException.with(aMessage);
        }
        return val;
    }

    default String assertArgumentMaxLength(final String val, final int length, final String aMessage) {
        if (val != null && val.length() > length) {
            throw DomainException.with(aMessage);
        }
        return val;
    }

    default void assertConditionTrue(final boolean val, final String aMessage) {
        if (!val) {
            throw DomainException.with(aMessage);
        }
    }
}
