package com.tubadev.receivables.domain.person;

import com.tubadev.receivables.domain.UnitTest;
import com.tubadev.receivables.domain.exceptions.DomainException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

class PhoneNumberTest extends UnitTest {

    @Test
    void givenFormattedNumber_whenCreating_shouldKeepDigitsOnly() {
        Assertions.assertEquals("5511988887777", PhoneNumber.of("+55 (11) 98888-7777").value());
    }

    @Test
    void givenBrazilianMobile_shouldMatchWithAndWithoutNinthDigit() {
        Assertions.assertEquals(Set.of("5511988887777", "551188887777"), new PhoneNumber("5511988887777").equivalents());
        Assertions.assertEquals(Set.of("551188887777", "5511988887777"), new PhoneNumber("551188887777").equivalents());
    }

    @Test
    void givenNonBrazilianNumber_shouldHaveNoEquivalents() {
        Assertions.assertEquals(Set.of("15551471409"), new PhoneNumber("15551471409").equivalents());
    }

    @Test
    void givenUsNumber_whenFormatted_shouldKeepDigitsOnly() {
        Assertions.assertEquals("15555550101", PhoneNumber.of("+1 (555) 555-0101").value());
    }

    @Test
    void givenCountryCodes_shouldMatchByPrefix() {
        final var us = new PhoneNumber("15555550101");
        final var br = new PhoneNumber("5511988887777");

        Assertions.assertTrue(us.hasCountryCodeIn(List.of("1")));
        Assertions.assertFalse(br.hasCountryCodeIn(List.of("1")));
        Assertions.assertTrue(br.hasCountryCodeIn(List.of("1", "55")));
        Assertions.assertFalse(us.hasCountryCodeIn(List.of()));
    }

    @Test
    void givenInvalidNumber_whenCreating_shouldThrow() {
        Assertions.assertThrows(DomainException.class, () -> PhoneNumber.of("abc"));
        Assertions.assertThrows(DomainException.class, () -> new PhoneNumber("0123"));
    }
}
