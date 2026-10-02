package com.tubadev.receivables.infrastructure.gateway.client.whatsapp.models;

/**
 * Body of {@code GET /{version}/{media-id}}: a short-lived URL (about 5 minutes) to download the media.
 */
public record WhatsAppMediaInfo(String id, String url, String mimeType, String sha256, Long fileSize) {}
