package com.tubadev.receivables.domain.message;

import com.tubadev.receivables.domain.conversation.ConversationId;

import java.util.List;
import java.util.Optional;

public interface MessageGateway {

    MessageId nextId();

    Optional<Message> messageOfId(MessageId anId);

    Optional<Message> messageOfWamid(String aWamid);

    List<Message> messagesOfConversation(ConversationId aConversationId);

    /**
     * Persists the aggregate and its pending domain events.
     *
     * @return the stored aggregate (next version, no pending events); keep using it if it will be changed again
     */
    Message save(Message aMessage);
}
