package com.tubadev.receivables.infrastructure.configuration.usecases;

import com.tubadev.receivables.application.campaign.CreateCampaign;
import com.tubadev.receivables.application.campaign.DispatchCampaign;
import com.tubadev.receivables.application.campaign.GetCampaign;
import com.tubadev.receivables.application.campaign.StartCampaign;
import com.tubadev.receivables.application.campaign.impl.DefaultCreateCampaign;
import com.tubadev.receivables.application.campaign.impl.DefaultDispatchCampaign;
import com.tubadev.receivables.application.campaign.impl.DefaultGetCampaign;
import com.tubadev.receivables.application.campaign.impl.DefaultStartCampaign;
import com.tubadev.receivables.application.message.SendMessage;
import com.tubadev.receivables.domain.campaign.CampaignGateway;
import com.tubadev.receivables.domain.conversation.ConversationGateway;
import com.tubadev.receivables.domain.customer.CustomerGateway;
import com.tubadev.receivables.domain.receivable.EligibilityGateway;
import com.tubadev.receivables.infrastructure.configuration.properties.CampaignProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class CampaignUseCaseConfig {

    @Bean
    CreateCampaign createCampaign(final CampaignGateway campaignGateway) {
        return new DefaultCreateCampaign(campaignGateway);
    }

    @Bean
    StartCampaign startCampaign(final CampaignGateway campaignGateway) {
        return new DefaultStartCampaign(campaignGateway);
    }

    @Bean
    GetCampaign getCampaign(final CampaignGateway campaignGateway) {
        return new DefaultGetCampaign(campaignGateway);
    }

    @Bean
    DispatchCampaign dispatchCampaign(
            final CampaignGateway campaignGateway,
            final Clock clock,
            final ConversationGateway conversationGateway,
            final CustomerGateway customerGateway,
            final EligibilityGateway eligibilityGateway,
            final SendMessage sendMessage,
            final CampaignProperties properties
    ) {
        return new DefaultDispatchCampaign(campaignGateway, clock, conversationGateway, customerGateway,
                eligibilityGateway, sendMessage, properties.dispatchConcurrency());
    }
}
