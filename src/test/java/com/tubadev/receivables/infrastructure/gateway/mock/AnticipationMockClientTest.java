package com.tubadev.receivables.infrastructure.gateway.mock;

import com.tubadev.receivables.domain.customer.CustomerId;
import com.tubadev.receivables.domain.receivable.AnticipationOffer;
import com.tubadev.receivables.domain.receivable.Money;
import com.tubadev.receivables.domain.receivable.OfferResult;
import com.tubadev.receivables.domain.receivable.Receivable;
import com.tubadev.receivables.infrastructure.configuration.properties.MockProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.stream.Collectors;

/**
 * Runs the mock against the sample portfolio shipped in {@code application.yml}, the same data the sandbox uses.
 */
@Tag("unitTest")
class AnticipationMockClientTest {

    private static final CustomerId SANDBOX = new CustomerId("cus_sandbox_01");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T15:00:00Z"), ZoneOffset.UTC);

    private final ReceivablesMockStore store = new ReceivablesMockStore(sampleData(), CLOCK);
    private final AnticipationMockClient target = new AnticipationMockClient(CLOCK, store);

    @ParameterizedTest
    @ValueSource(strings = {"500.00", "1234.56", "4789.57", "10000.00", "25000.00", "37000.00"})
    void givenRequestedAmount_shouldCombineSampleBoletosClosestToIt(final String requested) {
        final var offer = offered(Money.brl(requested));
        final var portfolio = store.eligibleReceivables(SANDBOX, LocalDate.parse("2026-10-01"));

        Assertions.assertTrue(portfolio.containsAll(offer.receivables()), "offer must only use boletos of the portfolio");
        Assertions.assertTrue(offer.netAmount().amount().compareTo(new BigDecimal(requested)) >= 0);
        Assertions.assertEquals(offer.grossAmount(), offer.receivables().stream().map(Receivable::amount).reduce(Money.zero(), Money::plus));
        Assertions.assertEquals(offer.netAmount(), offer.grossAmount().minus(offer.feeAmount()));
    }

    @Test
    void givenTheChatRequest_shouldNoLongerOfferTwiceTheAmount() {
        final var offer = offered(Money.brl("4789.57"));

        // before: the two earliest boletos, R$ 9.100,08 net. Now the closest combination of the same portfolio.
        Assertions.assertEquals(Money.brl("4814.34"), offer.netAmount(), () -> describe(offer));
        Assertions.assertEquals(4, offer.receivables().size());
    }

    @Test
    void givenRequestAbovePortfolio_shouldReportWhatIsAvailable() {
        final var result = Assertions.assertInstanceOf(OfferResult.ExceedsAvailable.class, target.offerFor(SANDBOX, Money.brl("100000.00")));

        Assertions.assertEquals(Money.brl("39455.06"), result.availableAmount());
    }

    @Test
    void givenLateNightInBrasilia_eligibilityAndOfferShouldAgreeOnTheAvailableAmount() {
        // 23:00 in Brasília is already the next day in UTC; both mocks must price with the business date
        final var lateNight = Clock.fixed(Instant.parse("2026-10-02T02:00:00Z"), ZoneOffset.UTC);
        final var aStore = new ReceivablesMockStore(sampleData(), lateNight);

        final var eligibility = new EligibilityMockClient(lateNight, aStore).eligibilityOf(SANDBOX);
        final var exceeds = Assertions.assertInstanceOf(OfferResult.ExceedsAvailable.class,
                new AnticipationMockClient(lateNight, aStore).offerFor(SANDBOX, Money.brl("100000.00")));

        Assertions.assertEquals(Money.brl("39455.06"), eligibility.availableAmount());
        Assertions.assertEquals(eligibility.availableAmount(), exceeds.availableAmount());
    }

    private AnticipationOffer offered(final Money requested) {
        return Assertions.assertInstanceOf(OfferResult.Offered.class, target.offerFor(SANDBOX, requested)).offer();
    }

    private static String describe(final AnticipationOffer offer) {
        return offer.receivables().stream().map(r -> r.payerName() + " " + r.amount().amount()).collect(Collectors.joining(", "));
    }

    private static MockProperties sampleData() {
        try {
            final var environment = new StandardEnvironment();
            new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                    .forEach(environment.getPropertySources()::addLast);
            return Binder.get(environment).bind("mock", MockProperties.class).get();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
