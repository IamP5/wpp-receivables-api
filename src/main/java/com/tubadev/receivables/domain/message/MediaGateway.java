package com.tubadev.receivables.domain.message;

import java.util.Optional;

/**
 * Port to the WhatsApp media store. Inbound media only carries an id that must be downloaded, and outbound
 * documents must be uploaded first so the message can reference them.
 */
public interface MediaGateway {

    /** Empty when the media expired (WhatsApp keeps it for a limited time) or can´t be downloaded. */
    Optional<MediaFile> download(String mediaId);

    /** The media id to reference in a message, or empty when the upload failed. */
    Optional<String> upload(MediaFile aFile);
}
