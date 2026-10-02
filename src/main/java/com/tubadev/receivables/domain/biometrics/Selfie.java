package com.tubadev.receivables.domain.biometrics;

import com.tubadev.receivables.domain.ValueObject;

import java.util.Arrays;

/**
 * Photo of the customer's face sent to prove that the person confirming the anticipation is the account holder.
 */
public record Selfie(byte[] content, String mimeType) implements ValueObject {

    public Selfie {
        this.assertArgumentNotNull(content, "'selfie.content' should not be null");
        this.assertConditionTrue(content.length > 0, "'selfie.content' should not be empty");
        this.assertArgumentNotEmpty(mimeType, "'selfie.mimeType' should not be empty");
    }

    public int size() {
        return content.length;
    }

    @Override
    public boolean equals(final Object o) {
        return o instanceof Selfie(var otherContent, var otherMimeType)
                && Arrays.equals(content, otherContent) && mimeType.equals(otherMimeType);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(content) + mimeType.hashCode();
    }

    @Override
    public String toString() {
        return "Selfie[mimeType=%s, size=%d]".formatted(mimeType, content.length);
    }
}
