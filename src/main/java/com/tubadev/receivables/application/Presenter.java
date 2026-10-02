package com.tubadev.receivables.application;

import java.util.function.Function;

@FunctionalInterface
public interface Presenter<UC_OUT, NEW_OUT> extends Function<UC_OUT, NEW_OUT> {
}
