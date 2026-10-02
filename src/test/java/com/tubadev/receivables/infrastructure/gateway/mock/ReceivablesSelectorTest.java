package com.tubadev.receivables.infrastructure.gateway.mock;

import com.tubadev.receivables.domain.receivable.Money;
import com.tubadev.receivables.domain.receivable.Receivable;
import com.tubadev.receivables.domain.receivable.ReceivableId;
import com.tubadev.receivables.infrastructure.gateway.mock.ReceivablesSelector.Candidate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

@Tag("unitTest")
class ReceivablesSelectorTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-01");

    @Test
    void givenExactCombination_shouldPickItOverTheEarliestBoletos() {
        final var eligible = List.of(candidate("a", "1000.00", 10), candidate("b", "3000.00", 20), candidate("c", "1500.00", 30));

        final var selected = ReceivablesSelector.select(eligible, Money.brl("2500.00")).orElseThrow();

        Assertions.assertEquals(List.of("a", "c"), ids(selected));
    }

    @Test
    void givenNoExactCombination_shouldPickTheSmallestExcess() {
        final var eligible = List.of(candidate("a", "6500.00", 10), candidate("b", "2750.00", 20), candidate("c", "2100.00", 30));

        final var selected = ReceivablesSelector.select(eligible, Money.brl("4789.57")).orElseThrow();

        Assertions.assertEquals(List.of("b", "c"), ids(selected));
    }

    @Test
    void givenSameExcess_shouldPreferFewerBoletosThenEarlierDueDates() {
        final var fewer = List.of(candidate("a", "1000.00", 10), candidate("b", "1000.00", 20), candidate("c", "2000.00", 30));
        Assertions.assertEquals(List.of("c"), ids(ReceivablesSelector.select(fewer, Money.brl("2000.00")).orElseThrow()));

        final var earlier = List.of(candidate("late", "1000.00", 40), candidate("early", "1000.00", 10));
        Assertions.assertEquals(List.of("early"), ids(ReceivablesSelector.select(earlier, Money.brl("900.00")).orElseThrow()));
    }

    @Test
    void givenRequestAboveEverything_shouldReturnEmpty() {
        final var eligible = List.of(candidate("a", "1000.00", 10), candidate("b", "500.00", 20));

        Assertions.assertTrue(ReceivablesSelector.select(eligible, Money.brl("1500.01")).isEmpty());
        Assertions.assertEquals(2, ReceivablesSelector.select(eligible, Money.brl("1500.00")).orElseThrow().size());
    }

    @Test
    void givenLargePortfolio_shouldStillAnswerWithinTheVisitBudget() {
        final var eligible = IntStream.range(0, 60)
                .mapToObj(i -> candidate("r" + i, "%d.%02d".formatted(300 + i * 137, i), 5 + i))
                .toList();

        final var selected = ReceivablesSelector.select(eligible, Money.brl("12345.67")).orElseThrow();

        final var net = selected.stream().map(Candidate::net).reduce(Money.zero(), Money::plus);
        Assertions.assertTrue(net.amount().compareTo(Money.brl("12345.67").amount()) >= 0);
    }

    private static Candidate candidate(final String id, final String net, final int dueInDays) {
        final var amount = Money.brl(net);
        final var receivable = new Receivable(new ReceivableId(id), "Sacado " + id, "34191.79001 01043.510047", TODAY.plusDays(dueInDays), amount);
        return new Candidate(receivable, Money.zero(), amount);
    }

    private static List<String> ids(final List<Candidate> selected) {
        return selected.stream().map(c -> c.receivable().id().value()).toList();
    }
}
