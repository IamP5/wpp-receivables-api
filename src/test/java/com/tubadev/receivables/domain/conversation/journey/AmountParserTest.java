package com.tubadev.receivables.domain.conversation.journey;

import com.tubadev.receivables.domain.UnitTest;
import com.tubadev.receivables.domain.receivable.Money;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AmountParserTest extends UnitTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "10000                  | 10000.00",
            "10.000                 | 10000.00",
            "r$ 10.000,00           | 10000.00",
            "r$10.000,50            | 10000.50",
            "10 mil                 | 10000.00",
            "1,5 mil                | 1500.00",
            "2.5k                   | 2500.00",
            "quero antecipar 25 mil | 25000.00",
            "7500,9                 | 7500.90",
            "1.250.000              | 1250000.00",
    })
    void givenTypedAmount_whenParse_shouldReturnMoney(final String typed, final String expected) {
        Assertions.assertEquals(Money.brl(expected), AmountParser.parse(Intent.normalize(typed)).orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "oi", "quero antecipar", "0", "r$ 0,00"})
    void givenTextWithoutAmount_whenParse_shouldReturnEmpty(final String typed) {
        Assertions.assertTrue(AmountParser.parse(Intent.normalize(typed)).isEmpty());
    }

    @Test
    void givenNull_whenParse_shouldReturnEmpty() {
        Assertions.assertTrue(AmountParser.parse(null).isEmpty());
    }
}
