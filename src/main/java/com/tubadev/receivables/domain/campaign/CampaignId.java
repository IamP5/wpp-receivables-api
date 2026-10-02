package com.tubadev.receivables.domain.campaign;

import com.tubadev.receivables.domain.Identifier;

public record CampaignId(String value) implements Identifier<String> {

    public CampaignId {
        this.assertArgumentNotEmpty(value, "'campaignId' should not be empty");
    }
}
