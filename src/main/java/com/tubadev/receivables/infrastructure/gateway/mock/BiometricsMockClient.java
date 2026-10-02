package com.tubadev.receivables.infrastructure.gateway.mock;

import com.tubadev.receivables.domain.biometrics.BiometricResult;
import com.tubadev.receivables.domain.biometrics.BiometricResult.Rejected;
import com.tubadev.receivables.domain.biometrics.BiometricsGateway;
import com.tubadev.receivables.domain.biometrics.Selfie;
import com.tubadev.receivables.domain.customer.CustomerId;
import com.tubadev.receivables.domain.utils.IdUtils;
import com.tubadev.receivables.infrastructure.configuration.properties.MockProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stand-in for the identity validation provider. Rules, in order:
 * <ol>
 *     <li>not a JPEG/PNG → {@code unsupported_media}</li>
 *     <li>smaller than {@code mock.biometrics.min-image-bytes} → {@code low_quality}</li>
 *     <li>the first {@code mock.biometrics.fail-first-attempts} selfies of a customer → {@code face_mismatch}
 *     (set it to exercise the retry path)</li>
 *     <li>otherwise approved with {@code mock.biometrics.approval-score}</li>
 * </ol>
 */
@Component
@ConditionalOnProperty(name = "biometrics.adapter", havingValue = "mock", matchIfMissing = true)
public class BiometricsMockClient implements BiometricsGateway {

    private static final Logger LOG = LoggerFactory.getLogger(BiometricsMockClient.class);
    private static final Set<String> SUPPORTED = Set.of("image/jpeg", "image/png");

    private final Clock clock;
    private final MockProperties.Biometrics policy;
    private final Map<CustomerId, Integer> attempts = new ConcurrentHashMap<>();

    public BiometricsMockClient(final Clock clock, final MockProperties properties) {
        this.clock = Objects.requireNonNull(clock);
        this.policy = Objects.requireNonNull(properties.biometrics());
    }

    @Override
    public BiometricResult verify(final CustomerId aCustomerId, final Selfie aSelfie) {
        final var attempt = attempts.merge(aCustomerId, 1, Integer::sum);
        final var result = evaluate(aSelfie, attempt);
        LOG.info("[biometrics-mock] customer {} attempt {} ({}): {}", aCustomerId.value(), attempt, aSelfie, result);
        return result;
    }

    private BiometricResult evaluate(final Selfie aSelfie, final int attempt) {
        if (!SUPPORTED.contains(aSelfie.mimeType().toLowerCase())) {
            return new Rejected(Rejected.UNSUPPORTED_MEDIA);
        }
        if (aSelfie.size() < policy.minImageBytes()) {
            return new Rejected(Rejected.LOW_QUALITY);
        }
        if (attempt <= policy.failFirstAttempts()) {
            return new Rejected(Rejected.FACE_MISMATCH);
        }
        return new BiometricResult.Approved("bio_" + IdUtils.uniqueId(), policy.approvalScore(), clock.instant());
    }
}
