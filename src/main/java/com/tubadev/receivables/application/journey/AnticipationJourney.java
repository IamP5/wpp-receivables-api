package com.tubadev.receivables.application.journey;

import com.tubadev.receivables.domain.conversation.Conversation;
import com.tubadev.receivables.domain.conversation.journey.Intent;
import com.tubadev.receivables.domain.customer.Customer;
import com.tubadev.receivables.domain.message.MessageContent;

import java.util.List;
import java.util.Optional;

/**
 * The conversational flow of the receivables anticipation. Given the current stage and what the customer
 * meant, it advances the conversation (mutating the aggregate) and returns the replies to send, in order.
 */
public abstract class AnticipationJourney {

    public abstract List<MessageContent> handle(Conversation aConversation, Optional<Customer> aCustomer, Intent anIntent);
}
