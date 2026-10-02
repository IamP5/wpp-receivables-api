package com.tubadev.receivables.domain.message.status;

import com.tubadev.receivables.domain.exceptions.DomainException;

/**
 * State pattern for the delivery lifecycle. States are immutable: every transition returns the next state.
 * <p>
 * WhatsApp status webhooks may arrive out of order (e.g. {@code read} before {@code delivered}), so the
 * default behaviour of every transition is to keep the current state, never regress.
 */
public sealed interface MessageStatus permits
        MessageStatus.Received,
        MessageStatus.Pending,
        MessageStatus.Accepted,
        MessageStatus.Sent,
        MessageStatus.Delivered,
        MessageStatus.Read,
        MessageStatus.Failed {

    String RECEIVED = "received";
    String PENDING = "pending";
    String ACCEPTED = "accepted";
    String SENT = "sent";
    String DELIVERED = "delivered";
    String READ = "read";
    String FAILED = "failed";

    default MessageStatus accept() { return this; }
    default MessageStatus sent() { return this; }
    default MessageStatus delivered() { return this; }
    default MessageStatus read() { return this; }
    default MessageStatus fail() { return this; }

    default String value() {
        return switch (this) {
            case Received _ -> RECEIVED;
            case Pending _ -> PENDING;
            case Accepted _ -> ACCEPTED;
            case Sent _ -> SENT;
            case Delivered _ -> DELIVERED;
            case Read _ -> READ;
            case Failed _ -> FAILED;
        };
    }

    static MessageStatus create(final String status) {
        if (status == null) {
            throw DomainException.with("'status' should not be null");
        }

        return switch (status) {
            case RECEIVED -> new Received();
            case PENDING -> new Pending();
            case ACCEPTED -> new Accepted();
            case SENT -> new Sent();
            case DELIVERED -> new Delivered();
            case READ -> new Read();
            case FAILED -> new Failed();
            default -> throw DomainException.with("Invalid message status: %s".formatted(status));
        };
    }

    /** Inbound messages have no delivery lifecycle. */
    record Received() implements MessageStatus {
        @Override public MessageStatus accept() { throw inbound(); }
        @Override public MessageStatus sent() { throw inbound(); }
        @Override public MessageStatus delivered() { throw inbound(); }
        @Override public MessageStatus read() { throw inbound(); }
        @Override public MessageStatus fail() { throw inbound(); }

        private static DomainException inbound() {
            return DomainException.with("Inbound messages can´t change their delivery status");
        }
    }

    /** Persisted, not yet accepted by the WhatsApp Cloud API. */
    record Pending() implements MessageStatus {
        @Override public MessageStatus accept() { return new Accepted(); }
        @Override public MessageStatus sent() { return new Sent(); }
        @Override public MessageStatus delivered() { return new Delivered(); }
        @Override public MessageStatus read() { return new Read(); }
        @Override public MessageStatus fail() { return new Failed(); }
    }

    /** Accepted by the Cloud API (we got a wamid), waiting for status webhooks. */
    record Accepted() implements MessageStatus {
        @Override public MessageStatus sent() { return new Sent(); }
        @Override public MessageStatus delivered() { return new Delivered(); }
        @Override public MessageStatus read() { return new Read(); }
        @Override public MessageStatus fail() { return new Failed(); }
    }

    record Sent() implements MessageStatus {
        @Override public MessageStatus delivered() { return new Delivered(); }
        @Override public MessageStatus read() { return new Read(); }
        @Override public MessageStatus fail() { return new Failed(); }
    }

    record Delivered() implements MessageStatus {
        @Override public MessageStatus read() { return new Read(); }
    }

    record Read() implements MessageStatus {}

    record Failed() implements MessageStatus {}
}
