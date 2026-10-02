package com.tubadev.receivables.infrastructure.rest.models.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendAgentMessageRequest(@NotBlank @Size(max = 4096) String text) {
}
