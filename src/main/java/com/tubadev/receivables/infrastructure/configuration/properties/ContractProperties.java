package com.tubadev.receivables.infrastructure.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Data printed on the contract document.
 *
 * @param assigneeName     the company buying the receivables (cessionária)
 * @param assigneeDocument its CNPJ (digits)
 * @param assigneeAddress  its address
 * @param city             jurisdiction (foro) and place of signature
 * @param disclaimer       when set, printed as a banner and watermark (e.g. documents issued with mock adapters)
 */
@ConfigurationProperties(prefix = "contract")
public record ContractProperties(
        String assigneeName,
        String assigneeDocument,
        String assigneeAddress,
        String city,
        String disclaimer
) {

    public boolean hasDisclaimer() {
        return disclaimer != null && !disclaimer.isBlank();
    }
}
