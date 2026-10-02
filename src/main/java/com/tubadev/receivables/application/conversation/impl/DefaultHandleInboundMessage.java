package com.tubadev.receivables.application.conversation.impl;

import com.tubadev.receivables.application.conversation.HandleInboundMessage;
import com.tubadev.receivables.application.journey.AnticipationJourney;
import com.tubadev.receivables.application.message.SendMessage;
import com.tubadev.receivables.domain.conversation.Conversation;
import com.tubadev.receivables.domain.conversation.ConversationCommand.Expire;
import com.tubadev.receivables.domain.conversation.ConversationCommand.RegisterInbound;
import com.tubadev.receivables.domain.conversation.ConversationGateway;
import com.tubadev.receivables.domain.conversation.ConversationId;
import com.tubadev.receivables.domain.conversation.SessionPolicy;
import com.tubadev.receivables.domain.conversation.journey.Intent;
import com.tubadev.receivables.domain.conversation.journey.Intent.StaleReply;
import com.tubadev.receivables.domain.customer.Customer;
import com.tubadev.receivables.domain.customer.CustomerGateway;
import com.tubadev.receivables.domain.message.Message;
import com.tubadev.receivables.domain.message.MessageContent;
import com.tubadev.receivables.domain.message.MessageContent.Buttons;
import com.tubadev.receivables.domain.message.MessageContent.Reply;
import com.tubadev.receivables.domain.message.MessageGateway;
import com.tubadev.receivables.domain.message.MessageId;
import com.tubadev.receivables.domain.person.PhoneNumber;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class DefaultHandleInboundMessage extends HandleInboundMessage {

    private final AnticipationJourney anticipationJourney;
    private final ConversationGateway conversationGateway;
    private final CustomerGateway customerGateway;
    private final MessageGateway messageGateway;
    private final SendMessage sendMessage;
    private final SessionPolicy sessionPolicy;

    public DefaultHandleInboundMessage(
            final AnticipationJourney anticipationJourney,
            final ConversationGateway conversationGateway,
            final CustomerGateway customerGateway,
            final MessageGateway messageGateway,
            final SendMessage sendMessage,
            final SessionPolicy sessionPolicy
    ) {
        this.anticipationJourney = Objects.requireNonNull(anticipationJourney);
        this.conversationGateway = Objects.requireNonNull(conversationGateway);
        this.customerGateway = Objects.requireNonNull(customerGateway);
        this.messageGateway = Objects.requireNonNull(messageGateway);
        this.sendMessage = Objects.requireNonNull(sendMessage);
        this.sessionPolicy = Objects.requireNonNull(sessionPolicy);
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
        final var current = sessionOf(phoneNumber, aCustomer, in.receivedAt());

        // the inbound is recorded before the journey runs: if a port fails, the message is not lost
        current.execute(new RegisterInbound(in.receivedAt()));
        final var aConversation = this.conversationGateway.save(current);

        final var anInbound = Message.newInbound(this.messageGateway.nextId(), aConversation.id(), in.wamid(), in.content());
        this.messageGateway.save(anInbound);

        final var replies = this.anticipationJourney.handle(aConversation, aCustomer, intentOf(in, aConversation));
        this.conversationGateway.save(aConversation);

        replies.forEach(reply -> this.sendMessage.execute(new SendReplyInput(aConversation.id(), reply)));

        return new StdOutput(aConversation.id(), anInbound.id(), aConversation.stage().value(), replies.size(), false);
    }

    /**
     * The open session, unless it was idle when the customer wrote (WhatsApp timestamp, so a late webhook redelivery
     * doesn't expire an active session): then it is closed and the message starts a new one.
     */
    private Conversation sessionOf(final PhoneNumber phoneNumber, final Optional<Customer> aCustomer, final Instant receivedAt) {
        final var open = this.conversationGateway.openConversationOf(phoneNumber);
        if (open.isPresent() && !open.get().isIdle(receivedAt, this.sessionPolicy)) {
            return open.get();
        }

        open.ifPresent(idle -> {
            idle.execute(new Expire());
            this.conversationGateway.save(idle);
        });
        return newConversation(phoneNumber, aCustomer);
    }

    /**
     * WhatsApp keeps old buttons tappable forever. A context dependent tap (confirm, cancel) only counts when it comes
     * from the latest message of this session offering that button.
     */
    private Intent intentOf(final Input in, final Conversation aConversation) {
        final var intent = Intent.of(in.content());
        if (in.replyTo() == null || !intent.dependsOnContext() || !(in.content() instanceof Reply(var buttonId, _))) {
            return intent;
        }

        final var origin = this.messageGateway.messageOfWamid(in.replyTo());
        if (origin.isEmpty()) {
            // not a message we sent (or sent before wamids were stored): nothing to compare with
            return intent;
        }
        if (!origin.get().conversationId().equals(aConversation.id())) {
            return new StaleReply(intent);
        }

        final var latestOffering = this.messageGateway.messagesOfConversation(aConversation.id()).stream()
                .filter(m -> !m.isInbound() && m.content() instanceof Buttons(_, var buttons)
                        && buttons.stream().anyMatch(b -> b.id().equalsIgnoreCase(buttonId)))
                .reduce((first, second) -> second);
        return latestOffering.isEmpty() || latestOffering.get().id().equals(origin.get().id()) ? intent : new StaleReply(intent);
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
