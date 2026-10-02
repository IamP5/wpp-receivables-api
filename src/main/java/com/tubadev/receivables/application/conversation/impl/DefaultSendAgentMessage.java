package com.tubadev.receivables.application.conversation.impl;

import com.tubadev.receivables.application.conversation.SendAgentMessage;
import com.tubadev.receivables.application.message.SendMessage;
import com.tubadev.receivables.domain.conversation.Conversation;
import com.tubadev.receivables.domain.conversation.ConversationCommand.AdvanceTo;
import com.tubadev.receivables.domain.conversation.ConversationGateway;
import com.tubadev.receivables.domain.conversation.ConversationId;
import com.tubadev.receivables.domain.conversation.journey.JourneyStage.HumanHandoff;
import com.tubadev.receivables.domain.exceptions.DomainException;
import com.tubadev.receivables.domain.message.MessageContent;
import com.tubadev.receivables.domain.message.MessageId;

import java.time.Clock;
import java.util.Objects;

public class DefaultSendAgentMessage extends SendAgentMessage {

    private final Clock clock;
    private final ConversationGateway conversationGateway;
    private final SendMessage sendMessage;

    public DefaultSendAgentMessage(final Clock clock, final ConversationGateway conversationGateway, final SendMessage sendMessage) {
        this.clock = Objects.requireNonNull(clock);
        this.conversationGateway = Objects.requireNonNull(conversationGateway);
        this.sendMessage = Objects.requireNonNull(sendMessage);
    }

    @Override
    public Output execute(final Input in) {
        if (in == null) {
            throw new IllegalArgumentException("Input of DefaultSendAgentMessage should not be null");
        }

        final var conversationId = new ConversationId(in.conversationId());
        final var content = new MessageContent.Text(in.text());

        final var aConversation = this.conversationGateway.conversationOfId(conversationId)
                .orElseThrow(() -> DomainException.notFound(Conversation.class, conversationId));

        if (!aConversation.isOpen()) {
            throw DomainException.with("Conversation %s is already finished".formatted(conversationId.value()));
        }

        aConversation.assertCanSend(content, clock.instant());

        if (!(aConversation.stage() instanceof HumanHandoff)) {
            aConversation.execute(new AdvanceTo(new HumanHandoff("agent_took_over")));
            this.conversationGateway.save(aConversation);
        }

        final var out = this.sendMessage.execute(new AgentSendInput(conversationId, content));
        return new StdOutput(out.messageId(), out.wamid(), out.status(), out.failureReason());
    }

    record AgentSendInput(ConversationId conversationId, MessageContent content) implements SendMessage.Input {
    }

    record StdOutput(MessageId messageId, String wamid, String status, String failureReason) implements Output {
    }
}
