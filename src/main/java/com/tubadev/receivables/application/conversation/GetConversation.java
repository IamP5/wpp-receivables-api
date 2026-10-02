package com.tubadev.receivables.application.conversation;

import com.tubadev.receivables.application.UseCase;
import com.tubadev.receivables.domain.conversation.Conversation;
import com.tubadev.receivables.domain.message.Message;

import java.util.List;

public abstract class GetConversation extends UseCase<GetConversation.Input, GetConversation.Output> {

    public interface Input {
        String conversationId();
    }

    public interface Output {
        Conversation conversation();
        List<Message> messages();
    }
}
