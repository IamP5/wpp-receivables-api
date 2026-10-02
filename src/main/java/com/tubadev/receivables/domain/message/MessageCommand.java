package com.tubadev.receivables.domain.message;

import com.tubadev.receivables.domain.AssertionConcern;

public sealed interface MessageCommand extends AssertionConcern {

    record MarkAccepted(String aWamid) implements MessageCommand {
        public MarkAccepted {
            this.assertArgumentNotEmpty(aWamid, "'wamid' should not be empty");
        }
    }

    record MarkSent() implements MessageCommand {}

    record MarkDelivered() implements MessageCommand {}

    record MarkRead() implements MessageCommand {}

    record MarkFailed(String aReason) implements MessageCommand {
        public MarkFailed {
            this.assertArgumentNotEmpty(aReason, "'reason' should not be empty");
        }
    }
}
