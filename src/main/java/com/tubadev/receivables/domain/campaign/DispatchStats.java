package com.tubadev.receivables.domain.campaign;

import com.tubadev.receivables.domain.ValueObject;

public record DispatchStats(int sent, int failed, int skipped) implements ValueObject {

    public static final DispatchStats EMPTY = new DispatchStats(0, 0, 0);

    public DispatchStats {
        this.assertConditionTrue(sent >= 0 && failed >= 0 && skipped >= 0, "'dispatchStats' should not be negative");
    }

    public int total() {
        return sent + failed + skipped;
    }
}
