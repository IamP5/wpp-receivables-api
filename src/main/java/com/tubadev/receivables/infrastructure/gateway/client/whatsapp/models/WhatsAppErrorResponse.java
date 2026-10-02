package com.tubadev.receivables.infrastructure.gateway.client.whatsapp.models;

/**
 * Graph API error envelope, e.g. code 131047 (re-engagement: outside the 24h window),
 * 131030 (recipient not in the allowed list of a test number), 130497 (country restriction).
 */
public record WhatsAppErrorResponse(Error error) {

    public record Error(String message, String type, Integer code, Integer errorSubcode, ErrorData errorData, String fbtraceId) {}

    public record ErrorData(String messagingProduct, String details) {}
}
