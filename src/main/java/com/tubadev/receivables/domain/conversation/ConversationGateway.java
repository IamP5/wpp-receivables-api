package com.tubadev.receivables.domain.conversation;

import com.tubadev.receivables.domain.person.PhoneNumber;

import java.util.Optional;

public interface ConversationGateway {

    ConversationId nextId();

    Optional<Conversation> conversationOfId(ConversationId anId);

    /** The ongoing (non terminal) conversation with this phone number, if any. */
    Optional<Conversation> openConversationOf(PhoneNumber aPhoneNumber);

    /**
     * Persists the aggregate and its pending domain events.
     *
     * @return the stored aggregate (next version, no pending events); keep using it if it will be changed again
     */
    Conversation save(Conversation aConversation);
}
