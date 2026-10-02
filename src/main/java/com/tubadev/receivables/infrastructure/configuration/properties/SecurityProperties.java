package com.tubadev.receivables.infrastructure.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param apiKey value expected in the {@code X-Api-Key} header of the internal API; blank disables the check
 */
@ConfigurationProperties(prefix = "security")
public record SecurityProperties(String apiKey) {

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
