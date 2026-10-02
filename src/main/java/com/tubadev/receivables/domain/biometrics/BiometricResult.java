package com.tubadev.receivables.domain.biometrics;

import java.math.BigDecimal;
import java.time.Instant;

public sealed interface BiometricResult {

    /** The face matches the customer's registered biometrics (and passed liveness, when the provider checks it). */
    record Approved(String verificationId, BigDecimal score, Instant verifiedAt) implements BiometricResult {}

    /** The selfie can´t be accepted; {@code reason} is one of the constants below or a provider specific code. */
    record Rejected(String reason) implements BiometricResult {

        public static final String UNSUPPORTED_MEDIA = "unsupported_media";
        public static final String LOW_QUALITY = "low_quality";
        public static final String NO_FACE_DETECTED = "no_face_detected";
        public static final String FACE_MISMATCH = "face_mismatch";
    }
}
