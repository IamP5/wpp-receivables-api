package com.tubadev.receivables.domain.contract;

import com.tubadev.receivables.domain.ValueObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * The rendered contract (PDF).
 */
public record ContractDocument(byte[] content, String filename) implements ValueObject {

    public static final String MIME_TYPE = "application/pdf";

    public ContractDocument {
        this.assertArgumentNotNull(content, "'document.content' should not be null");
        this.assertConditionTrue(content.length > 0, "'document.content' should not be empty");
        this.assertArgumentNotEmpty(filename, "'document.filename' should not be empty");
    }

    /** SHA-256 of the file, to prove later that a copy was not altered. */
    public String sha256() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean equals(final Object o) {
        return o instanceof ContractDocument(var otherContent, var otherFilename)
                && Arrays.equals(content, otherContent) && filename.equals(otherFilename);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(content) + filename.hashCode();
    }

    @Override
    public String toString() {
        return "ContractDocument[filename=%s, size=%d]".formatted(filename, content.length);
    }
}
