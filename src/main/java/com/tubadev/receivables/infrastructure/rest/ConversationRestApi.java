package com.tubadev.receivables.infrastructure.rest;

import com.tubadev.receivables.infrastructure.rest.models.req.SendAgentMessageRequest;
import com.tubadev.receivables.infrastructure.rest.models.res.ConversationResponse;
import com.tubadev.receivables.infrastructure.rest.models.res.SendAgentMessageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("conversations")
@Tag(name = "Conversation")
public interface ConversationRestApi {

    @GetMapping(value = "{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a conversation (journey stage) and its messages")
    ResponseEntity<ConversationResponse> get(@PathVariable String id);

    @PostMapping(value = "{id}/messages", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "A human agent replies (within the 24h window); the bot steps aside")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Sent (check status/failure_reason)"),
            @ApiResponse(responseCode = "422", description = "Outside the 24h window or conversation finished"),
    })
    ResponseEntity<SendAgentMessageResponse> sendAgentMessage(@PathVariable String id, @RequestBody @Valid SendAgentMessageRequest req);
}
