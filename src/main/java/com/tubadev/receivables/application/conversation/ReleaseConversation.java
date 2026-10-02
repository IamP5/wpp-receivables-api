package com.tubadev.receivables.application.conversation;

import com.tubadev.receivables.application.UseCase;
import com.tubadev.receivables.domain.conversation.ConversationId;

/**
 * An agent finishes a human handoff. The session is closed and the customer's next message starts a new one with
 * the bot. The customer is told so while WhatsApp still allows free-form messages (24h window).
 */
public abstract class ReleaseConversation extends UseCase<ReleaseConversation.Input, ReleaseConversation.Output> {

    public interface Input {
        String conversationId();
    }

    public interface Output {
        ConversationId conversationId();
        String stage();
        boolean customerNotified();
    }
}
