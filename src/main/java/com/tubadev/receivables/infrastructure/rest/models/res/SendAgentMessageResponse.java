package com.tubadev.receivables.infrastructure.rest.models.res;

import com.tubadev.receivables.application.conversation.SendAgentMessage;

public record SendAgentMessageResponse(String messageId, String wamid, String status, String failureReason) {

    public SendAgentMessageResponse(final SendAgentMessage.Output out) {
        this(out.messageId().value(), out.wamid(), out.status(), out.failureReason());
    }
}
