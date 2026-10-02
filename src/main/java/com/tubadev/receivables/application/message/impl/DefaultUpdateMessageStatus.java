package com.tubadev.receivables.application.message.impl;

import com.tubadev.receivables.application.message.UpdateMessageStatus;
import com.tubadev.receivables.domain.message.MessageCommand;
import com.tubadev.receivables.domain.message.MessageCommand.MarkDelivered;
import com.tubadev.receivables.domain.message.MessageCommand.MarkFailed;
import com.tubadev.receivables.domain.message.MessageCommand.MarkRead;
import com.tubadev.receivables.domain.message.MessageCommand.MarkSent;
import com.tubadev.receivables.domain.message.MessageGateway;
import com.tubadev.receivables.domain.message.status.MessageStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

public class DefaultUpdateMessageStatus extends UpdateMessageStatus {

    private static final Logger LOG = Logger.getLogger(DefaultUpdateMessageStatus.class.getName());

    private final MessageGateway messageGateway;

    public DefaultUpdateMessageStatus(final MessageGateway messageGateway) {
        this.messageGateway = Objects.requireNonNull(messageGateway);
    }

    @Override
    public void execute(final Input in) {
        if (in == null) {
            throw new IllegalArgumentException("Input of DefaultUpdateMessageStatus should not be null");
        }

        final var command = commandOf(in);
        if (command.isEmpty()) {
            LOG.fine(() -> "Ignoring unknown status %s for wamid %s".formatted(in.status(), in.wamid()));
            return;
        }

        this.messageGateway.messageOfWamid(in.wamid()).ifPresentOrElse(
                aMessage -> {
                    aMessage.execute(command.get());
                    this.messageGateway.save(aMessage);
                },
                () -> LOG.info(() -> "Status %s received for unknown wamid %s".formatted(in.status(), in.wamid()))
        );
    }

    private static Optional<MessageCommand> commandOf(final Input in) {
        return Optional.ofNullable(switch (in.status()) {
            case MessageStatus.SENT -> new MarkSent();
            case MessageStatus.DELIVERED -> new MarkDelivered();
            case MessageStatus.READ -> new MarkRead();
            case MessageStatus.FAILED -> new MarkFailed(in.errorReason() == null ? "unknown error" : in.errorReason());
            case null, default -> null;
        });
    }
}
