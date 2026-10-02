package com.tubadev.receivables.domain.campaign;

import java.util.Optional;

public interface CampaignGateway {

    CampaignId nextId();

    Optional<Campaign> campaignOfId(CampaignId anId);

    /**
     * Persists the aggregate and its pending domain events.
     *
     * @return the stored aggregate (next version, no pending events); keep using it if it will be changed again
     */
    Campaign save(Campaign aCampaign);
}
