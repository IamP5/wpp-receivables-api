package com.tubadev.receivables.domain.message;

import com.tubadev.receivables.domain.person.PhoneNumber;

/**
 * Port to the messaging channel (WhatsApp Cloud API). Implementations must never throw for business
 * rejections (e.g. 131047 re-engagement required): they are returned as {@link SendResult.Rejected}.
 */
public interface MessagingGateway {

    SendResult send(PhoneNumber to, MessageContent content);
}
