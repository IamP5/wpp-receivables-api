package com.tubadev.receivables.application.conversation;

import com.tubadev.receivables.application.UseCase;
import com.tubadev.receivables.domain.message.MessageId;

/**
 * A human agent replies in the conversation. The bot steps aside (stage becomes HUMAN_HANDOFF).
 */
public abstract class SendAgentMessage extends UseCase<SendAgentMessage.Input, SendAgentMessage.Output> {

    public interface Input {
        String conversationId();
        String text();
    }

    public interface Output {
        MessageId messageId();
        String wamid();
        String status();
        String failureReason();
    }
}
