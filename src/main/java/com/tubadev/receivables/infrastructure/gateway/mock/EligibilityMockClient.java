package com.tubadev.receivables.infrastructure.gateway.mock;

import com.tubadev.receivables.domain.customer.CustomerId;
import com.tubadev.receivables.domain.receivable.Eligibility;
import com.tubadev.receivables.domain.receivable.EligibilityGateway;
import com.tubadev.receivables.domain.receivable.Money;
import com.tubadev.receivables.domain.utils.InstantUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Available amount = what the customer would receive (net of fees) anticipating every eligible boleto.
 */
@Component
@ConditionalOnProperty(name = "receivables.adapter", havingValue = "mock", matchIfMissing = true)
public class EligibilityMockClient implements EligibilityGateway {

    private final Clock clock;
    private final ReceivablesMockStore store;

    public EligibilityMockClient(final Clock clock, final ReceivablesMockStore store) {
        this.clock = Objects.requireNonNull(clock);
        this.store = Objects.requireNonNull(store);
    }

    @Override
    public Eligibility eligibilityOf(final CustomerId aCustomerId) {
        if (store.customer(aCustomerId).isEmpty()) {
            return Eligibility.notEligible("customer_not_found");
        }

        final var today = LocalDate.ofInstant(clock.instant(), InstantUtils.BUSINESS_ZONE);
        final var pricing = new AnticipationPricing(store.policy().monthlyRate());
        final var eligible = store.eligibleReceivables(aCustomerId, today);
        final var available = eligible.stream()
                .map(r -> pricing.netOf(r, today))
                .reduce(Money.zero(), Money::plus);

        return Eligibility.of(available, eligible.size());
    }
}
