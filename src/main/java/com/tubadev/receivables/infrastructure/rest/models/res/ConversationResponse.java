package com.tubadev.receivables.infrastructure.rest.models.res;

import com.tubadev.receivables.application.conversation.GetConversation;
import com.tubadev.receivables.domain.conversation.journey.JourneyStage;
import com.tubadev.receivables.domain.message.Message;
import com.tubadev.receivables.domain.message.MessageContent;

import java.time.Instant;
import java.util.List;

public record ConversationResponse(
        String id,
        String phoneNumber,
        String customerId,
        String campaignId,
        String stage,
        JourneyStage stageData,
        boolean open,
        Instant lastInboundAt,
        Instant createdAt,
        List<MessageResponse> messages
) {

    public record MessageResponse(
            String id,
            String direction,
            String wamid,
            String type,
            MessageContent content,
            String status,
            String failureReason,
            Instant createdAt
    ) {
        MessageResponse(final Message m) {
            this(m.id().value(), m.direction().name(), m.wamid(), m.content().type(), m.content(),
                    m.status().value(), m.failureReason(), m.createdAt());
        }
    }

    public ConversationResponse(final GetConversation.Output out) {
        this(
                out.conversation().id().value(),
                out.conversation().phoneNumber().value(),
                out.conversation().customerId() == null ? null : out.conversation().customerId().value(),
                out.conversation().campaignId() == null ? null : out.conversation().campaignId().value(),
                out.conversation().stage().value(),
                out.conversation().stage(),
                out.conversation().isOpen(),
                out.conversation().lastInboundAt(),
                out.conversation().createdAt(),
                out.messages().stream().map(MessageResponse::new).toList()
        );
    }
}
