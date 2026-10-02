package com.tubadev.receivables.domain.conversation;

import com.tubadev.receivables.domain.AssertionConcern;
import com.tubadev.receivables.domain.conversation.journey.JourneyStage;

import java.time.Instant;

public sealed interface ConversationCommand extends AssertionConcern {

    /** The customer sent us a message: (re)opens the 24h customer service window. */
    record RegisterInbound(Instant receivedAt) implements ConversationCommand {
        public RegisterInbound {
            this.assertArgumentNotNull(receivedAt, "'receivedAt' should not be null");
        }
    }

    record AdvanceTo(JourneyStage next) implements ConversationCommand {
        public AdvanceTo {
            this.assertArgumentNotNull(next, "'next' should not be null");
        }
    }
}
