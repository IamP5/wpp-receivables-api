package com.tubadev.receivables.infrastructure.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequestMapping("webhooks/whatsapp")
@Tag(name = "WhatsApp Webhook")
public interface WebhookRestApi {

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Webhook verification handshake (Meta → App → WhatsApp → Configuration)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Echoes hub.challenge"),
            @ApiResponse(responseCode = "403", description = "Invalid verify token"),
    })
    ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge
    );

    @PostMapping
    @Operation(summary = "Receives inbound messages and delivery statuses")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Received"),
            @ApiResponse(responseCode = "401", description = "Invalid X-Hub-Signature-256"),
    })
    ResponseEntity<Void> receive(
            @RequestBody byte[] body,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature
    );
}
