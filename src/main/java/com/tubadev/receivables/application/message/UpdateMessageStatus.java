package com.tubadev.receivables.application.message;

import com.tubadev.receivables.application.UnitUseCase;

/**
 * Applies a delivery status reported by a WhatsApp status webhook (sent, delivered, read, failed).
 */
public abstract class UpdateMessageStatus extends UnitUseCase<UpdateMessageStatus.Input> {

    public interface Input {
        String wamid();
        String status();
        String errorReason();
    }
}
