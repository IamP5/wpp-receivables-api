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

    /** WhatsApp accepted a message from us (bot or agent): keeps a human handoff alive. */
    record RegisterOutbound(Instant sentAt) implements ConversationCommand {
        public RegisterOutbound {
            this.assertArgumentNotNull(sentAt, "'sentAt' should not be null");
        }
    }

    record AdvanceTo(JourneyStage next) implements ConversationCommand {
        public AdvanceTo {
            this.assertArgumentNotNull(next, "'next' should not be null");
        }
    }

    /** Ends an idle session; the caller checks {@link Conversation#isIdle} first. */
    record Expire() implements ConversationCommand {}

    /** An agent finished the human handoff: the next customer message starts a new session with the bot. */
    record Release() implements ConversationCommand {}
}
