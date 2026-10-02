package com.tubadev.receivables.infrastructure.observer.domainevent;

import com.tubadev.receivables.domain.DomainEvent;
import com.tubadev.receivables.domain.conversation.ConversationEnded;
import com.tubadev.receivables.infrastructure.observer.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Placeholder for the journey analytics/CRM integration: today it only logs the outcome of each conversation.
 */
@Component
public class JourneyOutcomeLogSubscriber implements Subscriber<DomainEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(JourneyOutcomeLogSubscriber.class);

    @Override
    public boolean test(final DomainEvent ev) {
        return ev instanceof ConversationEnded;
    }

    @Override
    public void onEvent(final DomainEvent ev) {
        if (ev instanceof ConversationEnded(var conversationId, var customerId, var campaignId, var outcome, var detail, _)) {
            LOG.info("Journey ended [conversation:{}] [customer:{}] [campaign:{}] [outcome:{}] [detail:{}]",
                    conversationId, customerId, campaignId, outcome, detail);
        }
    }
}
