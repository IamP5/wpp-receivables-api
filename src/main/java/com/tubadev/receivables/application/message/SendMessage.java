package com.tubadev.receivables.application.message;

import com.tubadev.receivables.application.UseCase;
import com.tubadev.receivables.domain.conversation.ConversationId;
import com.tubadev.receivables.domain.message.MessageContent;
import com.tubadev.receivables.domain.message.MessageId;

/**
 * Sends any outbound content through the messaging channel, enforcing the 24h service window rule
 * and recording the result.
 */
public abstract class SendMessage extends UseCase<SendMessage.Input, SendMessage.Output> {

    public interface Input {
        ConversationId conversationId();
        MessageContent content();
    }

    public interface Output {
        MessageId messageId();
        String wamid();
        String status();
        String failureReason();
    }
}
