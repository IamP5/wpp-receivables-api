package com.tubadev.receivables.domain.conversation.journey;

import com.tubadev.receivables.domain.receivable.Money;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Extracts the amount a customer typed in a free-form message, in the ways Brazilians usually write it:
 * "10000", "10.000", "R$ 10.000,00", "10 mil", "1,5 mil", "10k", "quero antecipar 25 mil".
 */
public final class AmountParser {

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1_000);
    private static final Pattern AMOUNT = Pattern.compile(
            "(?<int>\\d{1,3}(?:\\.\\d{3})+|\\d+)(?:[,.](?<dec>\\d{1,2})(?!\\d))?\\s*(?<mult>mil|k)?"
    );

    private AmountParser() {
    }

    public static Optional<Money> parse(final String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return Optional.empty();
        }

        final var matcher = AMOUNT.matcher(normalizedText.replace("r$", " "));
        if (!matcher.find()) {
            return Optional.empty();
        }

        final var integerPart = matcher.group("int").replace(".", "");
        final var decimalPart = matcher.group("dec");
        var amount = new BigDecimal(decimalPart == null ? integerPart : integerPart + "." + decimalPart);

        if (matcher.group("mult") != null) {
            amount = amount.multiply(THOUSAND);
        }

        return amount.signum() > 0 ? Optional.of(Money.brl(amount)) : Optional.empty();
    }
}
