package com.tubadev.receivables.infrastructure.rest.models.res;

import com.tubadev.receivables.domain.campaign.Campaign;

import java.time.Instant;
import java.util.List;

public record CampaignResponse(
        String id,
        String name,
        String templateName,
        String templateLanguage,
        List<String> templateParameters,
        int recipients,
        String status,
        int sent,
        int failed,
        int skipped,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {

    public CampaignResponse(final Campaign c) {
        this(
                c.id().value(),
                c.name(),
                c.template().name(),
                c.template().languageCode(),
                c.template().rawParameters(),
                c.recipients().size(),
                c.status().value(),
                c.stats().sent(),
                c.stats().failed(),
                c.stats().skipped(),
                c.createdAt(),
                c.startedAt(),
                c.finishedAt()
        );
    }
}
