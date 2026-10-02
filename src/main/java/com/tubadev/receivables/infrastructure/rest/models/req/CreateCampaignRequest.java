package com.tubadev.receivables.infrastructure.rest.models.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateCampaignRequest(
        @NotBlank String name,
        @NotBlank String templateName,
        @NotBlank String templateLanguage,
        List<String> templateParameters,
        @NotEmpty List<String> customerIds
) {
}
