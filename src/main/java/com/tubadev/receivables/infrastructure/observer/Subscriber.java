package com.tubadev.receivables.infrastructure.observer;

public interface Subscriber<T> {

    boolean test(T ev);

    void onEvent(T ev);
}
