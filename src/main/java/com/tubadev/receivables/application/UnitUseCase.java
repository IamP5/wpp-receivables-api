package com.tubadev.receivables.application;

public abstract class UnitUseCase<IN> {

    public abstract void execute(IN in);
}
