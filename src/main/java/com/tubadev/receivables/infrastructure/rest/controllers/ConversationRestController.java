package com.tubadev.receivables.infrastructure.rest.controllers;

import com.tubadev.receivables.application.conversation.GetConversation;
import com.tubadev.receivables.application.conversation.ReleaseConversation;
import com.tubadev.receivables.application.conversation.SendAgentMessage;
import com.tubadev.receivables.infrastructure.rest.ConversationRestApi;
import com.tubadev.receivables.infrastructure.rest.models.req.SendAgentMessageRequest;
import com.tubadev.receivables.infrastructure.rest.models.res.ConversationResponse;
import com.tubadev.receivables.infrastructure.rest.models.res.ReleaseConversationResponse;
import com.tubadev.receivables.infrastructure.rest.models.res.SendAgentMessageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
public class ConversationRestController implements ConversationRestApi {

    private final GetConversation getConversation;
    private final ReleaseConversation releaseConversation;
    private final SendAgentMessage sendAgentMessage;

    public ConversationRestController(
            final GetConversation getConversation,
            final ReleaseConversation releaseConversation,
            final SendAgentMessage sendAgentMessage
    ) {
        this.getConversation = Objects.requireNonNull(getConversation);
        this.releaseConversation = Objects.requireNonNull(releaseConversation);
        this.sendAgentMessage = Objects.requireNonNull(sendAgentMessage);
    }

    @Override
    public ResponseEntity<ConversationResponse> get(final String id) {
        record Input(String conversationId) implements GetConversation.Input {}
        return ResponseEntity.ok(this.getConversation.execute(new Input(id), ConversationResponse::new));
    }

    @Override
    public ResponseEntity<SendAgentMessageResponse> sendAgentMessage(final String id, final SendAgentMessageRequest req) {
        record Input(String conversationId, String text) implements SendAgentMessage.Input {}
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(this.sendAgentMessage.execute(new Input(id, req.text()), SendAgentMessageResponse::new));
    }

    @Override
    public ResponseEntity<ReleaseConversationResponse> release(final String id) {
        record Input(String conversationId) implements ReleaseConversation.Input {}
        return ResponseEntity.ok(this.releaseConversation.execute(new Input(id), ReleaseConversationResponse::new));
    }
}
