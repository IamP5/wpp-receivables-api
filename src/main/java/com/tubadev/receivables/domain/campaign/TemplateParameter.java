package com.tubadev.receivables.domain.campaign;

import com.tubadev.receivables.domain.ValueObject;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * A body parameter of an approved WhatsApp template. Placeholders are resolved per recipient at dispatch
 * time; anything else is sent as a literal.
 */
public sealed interface TemplateParameter extends ValueObject {

    String CUSTOMER_FIRST_NAME = "{{customer.first_name}}";
    String AVAILABLE_AMOUNT = "{{receivables.available_amount}}";
    String ELIGIBLE_COUNT = "{{receivables.eligible_count}}";

    String raw();

    String resolve(RecipientContext ctx);

    static TemplateParameter parse(final String raw) {
        return switch (raw) {
            case null -> new Literal("");
            case CUSTOMER_FIRST_NAME -> new CustomerFirstName();
            case AVAILABLE_AMOUNT -> new AvailableAmount();
            case ELIGIBLE_COUNT -> new EligibleCount();
            default -> new Literal(raw);
        };
    }

    record Literal(String value) implements TemplateParameter {
        @Override public String raw() { return value; }
        @Override public String resolve(final RecipientContext ctx) { return value; }
    }

    record CustomerFirstName() implements TemplateParameter {
        @Override public String raw() { return CUSTOMER_FIRST_NAME; }
        @Override public String resolve(final RecipientContext ctx) { return ctx.customer().firstName(); }
    }

    record AvailableAmount() implements TemplateParameter {
        private static final Locale PT_BR = Locale.of("pt", "BR");

        @Override public String raw() { return AVAILABLE_AMOUNT; }

        @Override
        public String resolve(final RecipientContext ctx) {
            return NumberFormat.getCurrencyInstance(PT_BR).format(ctx.eligibility().availableAmount().amount());
        }
    }

    record EligibleCount() implements TemplateParameter {
        @Override public String raw() { return ELIGIBLE_COUNT; }
        @Override public String resolve(final RecipientContext ctx) { return String.valueOf(ctx.eligibility().eligibleReceivables()); }
    }
}
