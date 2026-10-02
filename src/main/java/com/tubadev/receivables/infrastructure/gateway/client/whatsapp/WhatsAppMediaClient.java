package com.tubadev.receivables.infrastructure.gateway.client.whatsapp;

import com.tubadev.receivables.domain.message.MediaFile;
import com.tubadev.receivables.domain.message.MediaGateway;
import com.tubadev.receivables.infrastructure.configuration.annotations.WhatsApp;
import com.tubadev.receivables.infrastructure.configuration.properties.WhatsAppProperties;
import com.tubadev.receivables.infrastructure.gateway.client.whatsapp.models.WhatsAppMediaInfo;
import com.tubadev.receivables.infrastructure.gateway.client.whatsapp.models.WhatsAppMediaUploadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * Adapter of {@link MediaGateway} for the WhatsApp Cloud API media endpoints. Downloading takes two calls: the
 * media id resolves to a short-lived URL, which is fetched with the same access token.
 */
@Component
@ConditionalOnProperty(name = "whatsapp.adapter", havingValue = "cloud-api", matchIfMissing = true)
public class WhatsAppMediaClient implements MediaGateway {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsAppMediaClient.class);

    private final RestClient restClient;
    private final WhatsAppProperties properties;

    public WhatsAppMediaClient(@WhatsApp final RestClient restClient, final WhatsAppProperties properties) {
        this.restClient = Objects.requireNonNull(restClient);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public Optional<MediaFile> download(final String mediaId) {
        try {
            final var info = this.restClient.get()
                    .uri("/{version}/{mediaId}", properties.apiVersion(), mediaId)
                    .retrieve()
                    .body(WhatsAppMediaInfo.class);

            if (info == null || info.url() == null) {
                LOG.warn("WhatsApp media {} has no download URL", mediaId);
                return Optional.empty();
            }

            final var content = this.restClient.get()
                    .uri(URI.create(info.url()))
                    .retrieve()
                    .body(byte[].class);

            return Optional.ofNullable(content).map(bytes -> new MediaFile(bytes, info.mimeType(), null));
        } catch (final RestClientException ex) {
            LOG.warn("Error downloading WhatsApp media {}: {}", mediaId, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> upload(final MediaFile aFile) {
        final var fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(aFile.mimeType()));
        fileHeaders.setContentDisposition(ContentDisposition.formData()
                .name("file")
                .filename(aFile.filename() == null ? "file" : aFile.filename())
                .build());

        final var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("messaging_product", "whatsapp");
        parts.add("type", aFile.mimeType());
        parts.add("file", new HttpEntity<>(new ByteArrayResource(aFile.content()), fileHeaders));

        try {
            final var response = this.restClient.post()
                    .uri("/{version}/{phoneNumberId}/media", properties.apiVersion(), properties.phoneNumberId())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .body(WhatsAppMediaUploadResponse.class);

            return Optional.ofNullable(response).map(WhatsAppMediaUploadResponse::id);
        } catch (final RestClientException ex) {
            LOG.warn("Error uploading {} to WhatsApp: {}", aFile, ex.getMessage());
            return Optional.empty();
        }
    }
}
