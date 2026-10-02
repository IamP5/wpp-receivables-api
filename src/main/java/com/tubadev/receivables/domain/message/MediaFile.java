package com.tubadev.receivables.domain.message;

import com.tubadev.receivables.domain.ValueObject;

import java.util.Arrays;
import java.util.Objects;

/**
 * Binary content exchanged through WhatsApp: photos the customer sends and documents we send back.
 */
public record MediaFile(byte[] content, String mimeType, String filename) implements ValueObject {

    public MediaFile {
        this.assertArgumentNotNull(content, "'media.content' should not be null");
        this.assertArgumentNotEmpty(mimeType, "'media.mimeType' should not be empty");
    }

    public int size() {
        return content.length;
    }

    @Override
    public boolean equals(final Object o) {
        return o instanceof MediaFile(var otherContent, var otherMimeType, var otherFilename)
                && Arrays.equals(content, otherContent) && mimeType.equals(otherMimeType) && Objects.equals(filename, otherFilename);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(content), mimeType, filename);
    }

    @Override
    public String toString() {
        return "MediaFile[mimeType=%s, filename=%s, size=%d]".formatted(mimeType, filename, content.length);
    }
}
