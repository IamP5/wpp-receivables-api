package com.tubadev.receivables.application.conversation.impl;

import com.tubadev.receivables.application.conversation.HandleInboundMessage;
import com.tubadev.receivables.application.journey.AnticipationJourney;
import com.tubadev.receivables.application.message.SendMessage;
import com.tubadev.receivables.domain.conversation.Conversation;
import com.tubadev.receivables.domain.conversation.ConversationCommand.RegisterInbound;
import com.tubadev.receivables.domain.conversation.ConversationGateway;
import com.tubadev.receivables.domain.conversation.ConversationId;
import com.tubadev.receivables.domain.conversation.journey.Intent;
import com.tubadev.receivables.domain.customer.Customer;
import com.tubadev.receivables.domain.customer.CustomerGateway;
import com.tubadev.receivables.domain.message.Message;
import com.tubadev.receivables.domain.message.MessageContent;
import com.tubadev.receivables.domain.message.MessageGateway;
import com.tubadev.receivables.domain.message.MessageId;
import com.tubadev.receivables.domain.person.PhoneNumber;

import java.util.Objects;
import java.util.Optional;

public class DefaultHandleInboundMessage extends HandleInboundMessage {

    private final AnticipationJourney anticipationJourney;
    private final ConversationGateway conversationGateway;
    private final CustomerGateway customerGateway;
    private final MessageGateway messageGateway;
    private final SendMessage sendMessage;

    public DefaultHandleInboundMessage(
            final AnticipationJourney anticipationJourney,
            final ConversationGateway conversationGateway,
            final CustomerGateway customerGateway,
            final MessageGateway messageGateway,
            final SendMessage sendMessage
    ) {
        this.anticipationJourney = Objects.requireNonNull(anticipationJourney);
        this.conversationGateway = Objects.requireNonNull(conversationGateway);
        this.customerGateway = Objects.requireNonNull(customerGateway);
        this.messageGateway = Objects.requireNonNull(messageGateway);
        this.sendMessage = Objects.requireNonNull(sendMessage);
    }

    @Override
    public Output execute(final Input in) {
        if (in == null) {
            throw new IllegalArgumentException("Input of DefaultHandleInboundMessage should not be null");
        }

        final var duplicated = this.messageGateway.messageOfWamid(in.wamid());
        if (duplicated.isPresent()) {
            return new StdOutput(duplicated.get().conversationId(), duplicated.get().id(), null, 0, true);
        }

        final var phoneNumber = PhoneNumber.of(in.from());
        final var aCustomer = this.customerGateway.customerOfPhoneNumber(phoneNumber);
        final var current = this.conversationGateway.openConversationOf(phoneNumber)
                .orElseGet(() -> newConversation(phoneNumber, aCustomer));

        // the inbound is recorded before the journey runs: if a port fails, the message is not lost
        current.execute(new RegisterInbound(in.receivedAt()));
        final var aConversation = this.conversationGateway.save(current);

        final var anInbound = Message.newInbound(this.messageGateway.nextId(), aConversation.id(), in.wamid(), in.content());
        this.messageGateway.save(anInbound);

        final var replies = this.anticipationJourney.handle(aConversation, aCustomer, Intent.of(in.content()));
        this.conversationGateway.save(aConversation);

        replies.forEach(reply -> this.sendMessage.execute(new SendReplyInput(aConversation.id(), reply)));

        return new StdOutput(aConversation.id(), anInbound.id(), aConversation.stage().value(), replies.size(), false);
    }

    private Conversation newConversation(final PhoneNumber phoneNumber, final Optional<Customer> aCustomer) {
        return Conversation.newConversation(
                this.conversationGateway.nextId(),
                phoneNumber,
                aCustomer.map(Customer::id).orElse(null),
                null
        );
    }

    record SendReplyInput(ConversationId conversationId, MessageContent content) implements SendMessage.Input {
    }

    record StdOutput(ConversationId conversationId, MessageId messageId, String stage, int repliesSent, boolean duplicated)
            implements Output {
    }
}
