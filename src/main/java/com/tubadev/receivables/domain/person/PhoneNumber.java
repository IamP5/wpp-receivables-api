package com.tubadev.receivables.domain.person;

import com.tubadev.receivables.domain.ValueObject;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Phone number in the WhatsApp {@code wa_id} format: E.164 digits without the leading '+'.
 */
public record PhoneNumber(String value) implements ValueObject {

    private static final Pattern E164_DIGITS = Pattern.compile("^[1-9]\\d{7,14}$");
    private static final String BRAZIL_DDI = "55";

    public PhoneNumber {
        this.assertArgumentNotEmpty(value, "'phoneNumber' should not be empty");
        this.assertConditionTrue(E164_DIGITS.matcher(value).matches(), "'phoneNumber' should be in E.164 format (digits only)");
    }

    public static PhoneNumber of(final String raw) {
        return new PhoneNumber(raw == null ? null : raw.replaceAll("\\D", ""));
    }

    /**
     * Whether the number starts with one of the given country calling codes (e.g. {@code "1"} for US/Canada,
     * {@code "55"} for Brazil).
     */
    public boolean hasCountryCodeIn(final Collection<String> callingCodes) {
        return callingCodes.stream().anyMatch(value::startsWith);
    }

    /**
     * WhatsApp may report Brazilian mobile numbers without the 9th digit (e.g. 55 11 8888-7777 instead of
     * 55 11 98888-7777), so both representations must resolve to the same customer.
     */
    public Set<String> equivalents() {
        final var keys = new LinkedHashSet<String>();
        keys.add(value);

        if (value.startsWith(BRAZIL_DDI)) {
            if (value.length() == 13 && value.charAt(4) == '9') {
                keys.add(value.substring(0, 4) + value.substring(5));
            } else if (value.length() == 12 && value.charAt(4) >= '6') {
                keys.add(value.substring(0, 4) + "9" + value.substring(4));
            }
        }
        return Set.copyOf(keys);
    }
}
