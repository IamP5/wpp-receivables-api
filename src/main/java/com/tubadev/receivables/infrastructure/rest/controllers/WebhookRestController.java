package com.tubadev.receivables.infrastructure.rest.controllers;

import com.tubadev.receivables.infrastructure.configuration.properties.WhatsAppProperties;
import com.tubadev.receivables.infrastructure.configuration.security.WebhookSignatureVerifier;
import com.tubadev.receivables.infrastructure.json.Json;
import com.tubadev.receivables.infrastructure.mediator.WebhookMediator;
import com.tubadev.receivables.infrastructure.rest.WebhookRestApi;
import com.tubadev.receivables.infrastructure.rest.models.webhook.WebhookPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
public class WebhookRestController implements WebhookRestApi {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookRestController.class);
    private static final String SUBSCRIBE = "subscribe";

    private final WebhookMediator webhookMediator;
    private final WebhookSignatureVerifier signatureVerifier;
    private final WhatsAppProperties properties;

    public WebhookRestController(
            final WebhookMediator webhookMediator,
            final WebhookSignatureVerifier signatureVerifier,
            final WhatsAppProperties properties
    ) {
        this.webhookMediator = Objects.requireNonNull(webhookMediator);
        this.signatureVerifier = Objects.requireNonNull(signatureVerifier);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public ResponseEntity<String> verify(final String mode, final String verifyToken, final String challenge) {
        final var configured = properties.verifyToken();
        if (SUBSCRIBE.equals(mode) && configured != null && !configured.isBlank() && configured.equals(verifyToken)) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @Override
    public ResponseEntity<Void> receive(final byte[] body, final String signature) {
        if (!signatureVerifier.isValid(body, signature)) {
            LOG.warn("Webhook rejected: invalid X-Hub-Signature-256");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        final WebhookPayload payload;
        try {
            payload = Json.readValue(body, WebhookPayload.class);
        } catch (final RuntimeException ex) {
            LOG.warn("Webhook discarded: unparseable payload", ex);
            return ResponseEntity.ok().build();
        }

        this.webhookMediator.process(payload);
        return ResponseEntity.ok().build();
    }
}
