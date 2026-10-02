package com.tubadev.receivables.infrastructure.gateway.client.whatsapp.models;

import java.util.List;

public record WhatsAppMessageResponse(String messagingProduct, List<Contact> contacts, List<MessageRef> messages) {

    public record Contact(String input, String waId) {}

    public record MessageRef(String id, String messageStatus) {}

    public String firstMessageId() {
        return messages == null || messages.isEmpty() ? null : messages.getFirst().id();
    }
}
