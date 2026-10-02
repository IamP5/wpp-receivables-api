package com.tubadev.receivables.domain.biometrics;

import com.tubadev.receivables.domain.customer.CustomerId;

/**
 * Port to the identity validation provider: matches the selfie against the biometrics registered for the
 * customer (face match, liveness, fraud checks).
 */
public interface BiometricsGateway {

    BiometricResult verify(CustomerId aCustomerId, Selfie aSelfie);
}
