package com.tubadev.receivables.infrastructure.gateway.mock;

import com.tubadev.receivables.domain.customer.CustomerId;
import com.tubadev.receivables.domain.receivable.AnticipationGateway;
import com.tubadev.receivables.domain.receivable.AnticipationOffer;
import com.tubadev.receivables.domain.receivable.AnticipationResult;
import com.tubadev.receivables.domain.receivable.Money;
import com.tubadev.receivables.domain.receivable.OfferResult;
import com.tubadev.receivables.domain.receivable.Receivable;
import com.tubadev.receivables.domain.utils.IdUtils;
import com.tubadev.receivables.domain.utils.InstantUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Offers the combination of the customer's eligible boletos whose net amount gets closest to (and not below) what
 * they asked for. See {@link ReceivablesSelector}.
 */
@Component
@ConditionalOnProperty(name = "receivables.adapter", havingValue = "mock", matchIfMissing = true)
public class AnticipationMockClient implements AnticipationGateway {

    private static final DateTimeFormatter PROTOCOL_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final Clock clock;
    private final ReceivablesMockStore store;

    public AnticipationMockClient(final Clock clock, final ReceivablesMockStore store) {
        this.clock = Objects.requireNonNull(clock);
        this.store = Objects.requireNonNull(store);
    }

    @Override
    public OfferResult offerFor(final CustomerId aCustomerId, final Money requestedAmount) {
        if (store.customer(aCustomerId).isEmpty()) {
            return new OfferResult.NotEligible("customer_not_found");
        }

        final var minimum = Money.brl(store.policy().minimumAmount());
        if (requestedAmount.amount().compareTo(minimum.amount()) < 0) {
            return new OfferResult.BelowMinimum(requestedAmount, minimum);
        }

        final var today = today();
        final var pricing = new AnticipationPricing(store.policy().monthlyRate());
        final var eligible = store.eligibleReceivables(aCustomerId, today);
        if (eligible.isEmpty()) {
            return new OfferResult.NotEligible("no_eligible_receivables");
        }

        final var candidates = eligible.stream()
                .map(r -> new ReceivablesSelector.Candidate(r, pricing.feeOf(r, today), pricing.netOf(r, today)))
                .toList();
        final var selection = ReceivablesSelector.select(candidates, requestedAmount);
        if (selection.isEmpty()) {
            final var available = candidates.stream().map(ReceivablesSelector.Candidate::net).reduce(Money.zero(), Money::plus);
            return new OfferResult.ExceedsAvailable(requestedAmount, available);
        }

        final var selected = selection.get();
        final var gross = selected.stream().map(c -> c.receivable().amount()).reduce(Money.zero(), Money::plus);
        final var fee = selected.stream().map(ReceivablesSelector.Candidate::fee).reduce(Money.zero(), Money::plus);
        final var net = selected.stream().map(ReceivablesSelector.Candidate::net).reduce(Money.zero(), Money::plus);

        final var offer = new AnticipationOffer(
                "off_" + IdUtils.uniqueId(),
                requestedAmount,
                selected.stream().map(ReceivablesSelector.Candidate::receivable).toList(),
                gross,
                fee,
                net,
                store.policy().monthlyRate(),
                clock.instant().plus(store.policy().offerTtl())
        );
        store.saveOffer(aCustomerId, offer);
        return new OfferResult.Offered(offer);
    }

    @Override
    public AnticipationResult request(final CustomerId aCustomerId, final String offerId) {
        final var stored = store.offer(offerId).filter(o -> o.customerId().equals(aCustomerId));
        if (stored.isEmpty()) {
            return new AnticipationResult.Refused("proposta não encontrada");
        }

        final var offer = stored.get().offer();
        if (clock.instant().isAfter(offer.validUntil())) {
            return new AnticipationResult.Refused("a proposta expirou");
        }

        if (!store.anticipate(offer.receivables().stream().map(Receivable::id).toList())) {
            return new AnticipationResult.Refused("alguns boletos não estão mais disponíveis");
        }

        final var protocol = "ANT-%s-%s".formatted(PROTOCOL_DATE.format(today()), IdUtils.uniqueId().substring(0, 6).toUpperCase());
        return new AnticipationResult.Requested(protocol, offer.netAmount(), nextBusinessDay(today()), offer);
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), InstantUtils.BUSINESS_ZONE);
    }

    private static LocalDate nextBusinessDay(final LocalDate from) {
        var day = from.plusDays(1);
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            day = day.plusDays(1);
        }
        return day;
    }
}
