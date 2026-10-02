package com.tubadev.receivables.domain.message;

public sealed interface SendResult {

    record Accepted(String wamid) implements SendResult {}

    record Rejected(String code, String reason) implements SendResult {

        public String describe() {
            return code == null ? reason : "[%s] %s".formatted(code, reason);
        }
    }
}
