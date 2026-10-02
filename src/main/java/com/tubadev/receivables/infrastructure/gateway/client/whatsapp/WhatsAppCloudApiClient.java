package com.tubadev.receivables.infrastructure.gateway.client.whatsapp;

import com.tubadev.receivables.domain.message.MessageContent;
import com.tubadev.receivables.domain.message.MessagingGateway;
import com.tubadev.receivables.domain.message.SendResult;
import com.tubadev.receivables.domain.person.PhoneNumber;
import com.tubadev.receivables.infrastructure.configuration.annotations.WhatsApp;
import com.tubadev.receivables.infrastructure.configuration.properties.WhatsAppProperties;
import com.tubadev.receivables.infrastructure.gateway.client.whatsapp.models.WhatsAppErrorResponse;
import com.tubadev.receivables.infrastructure.gateway.client.whatsapp.models.WhatsAppMessageRequest;
import com.tubadev.receivables.infrastructure.gateway.client.whatsapp.models.WhatsAppMessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Objects;

/**
 * Adapter of {@link MessagingGateway} for the WhatsApp Cloud API. Every failure is translated into
 * {@link SendResult.Rejected}, so the caller always records the outcome of the attempt.
 */
@Component
@ConditionalOnProperty(name = "whatsapp.adapter", havingValue = "cloud-api", matchIfMissing = true)
public class WhatsAppCloudApiClient implements MessagingGateway {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsAppCloudApiClient.class);

    private final RestClient restClient;
    private final WhatsAppProperties properties;

    public WhatsAppCloudApiClient(@WhatsApp final RestClient restClient, final WhatsAppProperties properties) {
        this.restClient = Objects.requireNonNull(restClient);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public SendResult send(final PhoneNumber to, final MessageContent content) {
        if (!properties.allowsRecipient(to)) {
            return RecipientNotAllowed.of(to, properties);
        }

        final var request = WhatsAppMessageRequest.of(to.value(), content);

        try {
            return this.restClient.post()
                    .uri("/{version}/{phoneNumberId}/messages", properties.apiVersion(), properties.phoneNumberId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((req, res) -> {
                        if (res.getStatusCode().is2xxSuccessful()) {
                            final var body = res.bodyTo(WhatsAppMessageResponse.class);
                            final var wamid = body == null ? null : body.firstMessageId();
                            return wamid == null
                                    ? new SendResult.Rejected("empty_response", "Cloud API accepted the request without a message id")
                                    : new SendResult.Accepted(wamid);
                        }
                        return rejectionOf(res.getStatusCode().value(), res.bodyTo(WhatsAppErrorResponse.class));
                    });
        } catch (final RestClientException ex) {
            LOG.warn("Error calling the WhatsApp Cloud API [type:{}]: {}", content.type(), ex.getMessage());
            return new SendResult.Rejected("transport_error", ex.getMessage());
        }
    }

    private static SendResult.Rejected rejectionOf(final int httpStatus, final WhatsAppErrorResponse response) {
        if (response == null || response.error() == null) {
            return new SendResult.Rejected("http_" + httpStatus, "Cloud API answered with HTTP " + httpStatus);
        }

        final var error = response.error();
        final var details = error.errorData() == null ? null : error.errorData().details();
        final var reason = details == null ? error.message() : "%s: %s".formatted(error.message(), details);
        LOG.warn("WhatsApp Cloud API rejected the message [status:{}] [code:{}] [fbtrace:{}]: {}",
                httpStatus, error.code(), error.fbtraceId(), reason);
        return new SendResult.Rejected(error.code() == null ? "http_" + httpStatus : String.valueOf(error.code()), reason);
    }
}
