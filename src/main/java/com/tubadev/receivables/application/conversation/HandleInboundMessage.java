package com.tubadev.receivables.application.conversation;

import com.tubadev.receivables.application.UseCase;
import com.tubadev.receivables.domain.conversation.ConversationId;
import com.tubadev.receivables.domain.message.MessageContent;
import com.tubadev.receivables.domain.message.MessageId;

import java.time.Instant;

/**
 * Entry point of the journey: a WhatsApp user sent us a message.
 * Idempotent by {@code wamid}, because Meta retries webhooks. An idle session is closed first, and the message opens
 * a new one.
 */
public abstract class HandleInboundMessage extends UseCase<HandleInboundMessage.Input, HandleInboundMessage.Output> {

    public interface Input {
        String wamid();
        String from();
        MessageContent content();
        Instant receivedAt();

        /** The wamid of the message this one answers (WhatsApp sends it on button taps), null otherwise. */
        default String replyTo() {
            return null;
        }
    }

    public interface Output {
        ConversationId conversationId();
        MessageId messageId();
        String stage();
        int repliesSent();
        boolean duplicated();
    }
}
