package com.tubadev.receivables.infrastructure.gateway.mock;

import com.tubadev.receivables.domain.message.MediaFile;
import com.tubadev.receivables.domain.message.MediaGateway;
import com.tubadev.receivables.domain.utils.IdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Media store used with {@code whatsapp.adapter=mock}. Downloads return a synthetic photo (a media id containing
 * {@code small} returns a tiny one, to simulate a low quality selfie), and uploads are written to a temporary
 * folder so the generated documents can be opened locally.
 */
@Component
@ConditionalOnProperty(name = "whatsapp.adapter", havingValue = "mock")
public class MediaMockClient implements MediaGateway {

    private static final Logger LOG = LoggerFactory.getLogger(MediaMockClient.class);
    private static final Path UPLOADS = Path.of(System.getProperty("java.io.tmpdir"), "wpp-mock-media");

    @Override
    public Optional<MediaFile> download(final String mediaId) {
        if (mediaId.contains("expired")) {
            return Optional.empty();
        }
        final var size = mediaId.contains("small") ? 1_024 : 64 * 1_024;
        return Optional.of(new MediaFile(new byte[size], "image/jpeg", null));
    }

    @Override
    public Optional<String> upload(final MediaFile aFile) {
        final var mediaId = "mock-media-" + IdUtils.uniqueId();
        try {
            Files.createDirectories(UPLOADS);
            final var target = UPLOADS.resolve(mediaId + "-" + (aFile.filename() == null ? "file" : aFile.filename()));
            Files.write(target, aFile.content());
            LOG.info("[whatsapp-mock] media uploaded: {}", target);
        } catch (final IOException ex) {
            LOG.warn("[whatsapp-mock] could not keep a copy of {}: {}", aFile, ex.getMessage());
        }
        return Optional.of(mediaId);
    }
}
