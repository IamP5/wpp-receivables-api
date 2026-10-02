package com.tubadev.receivables.domain;

public interface Identifier<T> extends ValueObject {
    T value();
}
