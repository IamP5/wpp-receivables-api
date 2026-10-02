package com.tubadev.receivables.infrastructure.rest.models.res;

import com.tubadev.receivables.application.conversation.ReleaseConversation;

public record ReleaseConversationResponse(String conversationId, String stage, boolean customerNotified) {

    public ReleaseConversationResponse(final ReleaseConversation.Output out) {
        this(out.conversationId().value(), out.stage(), out.customerNotified());
    }
}
