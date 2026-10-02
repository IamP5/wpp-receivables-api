package com.tubadev.receivables.domain.person;

import com.tubadev.receivables.domain.ValueObject;
import com.tubadev.receivables.domain.exceptions.DomainException;

public sealed interface Document extends ValueObject {

    String value();

    String type();

    static Document create(final String documentNumber, final String documentType) {
        if (documentType == null) {
            throw DomainException.with("'documentType' should not be null");
        }

        final var digits = documentNumber == null ? null : documentNumber.replaceAll("\\D", "");
        return switch (documentType.toLowerCase()) {
            case Cpf.TYPE -> new Cpf(digits);
            case Cnpj.TYPE -> new Cnpj(digits);
            default -> throw DomainException.with("Invalid document type: %s".formatted(documentType));
        };
    }

    record Cpf(String value) implements Document {
        public static final String TYPE = "cpf";

        public Cpf {
            this.assertArgumentNotEmpty(value, "'cpf' should not be empty");
            this.assertArgumentLength(value, 11, "'cpf' is invalid");
        }

        @Override
        public String type() {
            return TYPE;
        }
    }

    record Cnpj(String value) implements Document {
        public static final String TYPE = "cnpj";

        public Cnpj {
            this.assertArgumentNotEmpty(value, "'cnpj' should not be empty");
            this.assertArgumentLength(value, 14, "'cnpj' is invalid");
        }

        @Override
        public String type() {
            return TYPE;
        }
    }
}
