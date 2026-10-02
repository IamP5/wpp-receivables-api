package com.tubadev.receivables.application.conversation;

import com.tubadev.receivables.application.UseCase;
import com.tubadev.receivables.domain.conversation.ConversationId;
import com.tubadev.receivables.domain.message.MessageContent;
import com.tubadev.receivables.domain.message.MessageId;

import java.time.Instant;

/**
 * Entry point of the journey: a WhatsApp user sent us a message.
 * Idempotent by {@code wamid}, because Meta retries webhooks.
 */
public abstract class HandleInboundMessage extends UseCase<HandleInboundMessage.Input, HandleInboundMessage.Output> {

    public interface Input {
        String wamid();
        String from();
        MessageContent content();
        Instant receivedAt();
    }

    public interface Output {
        ConversationId conversationId();
        MessageId messageId();
        String stage();
        int repliesSent();
        boolean duplicated();
    }
}
