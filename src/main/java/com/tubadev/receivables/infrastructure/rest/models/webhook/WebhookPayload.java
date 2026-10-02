package com.tubadev.receivables.infrastructure.rest.models.webhook;

import com.tubadev.receivables.domain.message.MessageContent;

import java.util.List;
import java.util.stream.Stream;

/**
 * WhatsApp Business Account webhook ({@code object = whatsapp_business_account}, field {@code messages}).
 * See https://developers.facebook.com/docs/whatsapp/cloud-api/webhooks/components
 */
public record WebhookPayload(String object, List<Entry> entry) {

    public record Entry(String id, List<Change> changes) {}

    public record Change(String field, Value value) {}

    public record Value(
            String messagingProduct,
            Metadata metadata,
            List<Contact> contacts,
            List<InboundMessage> messages,
            List<Status> statuses
    ) {}

    public record Metadata(String displayPhoneNumber, String phoneNumberId) {}

    public record Contact(Profile profile, String waId) {}

    public record Profile(String name) {}

    public record InboundMessage(
            String from,
            String id,
            String timestamp,
            String type,
            TextBody text,
            ButtonBody button,
            Interactive interactive,
            MediaBody image,
            MediaBody document,
            MediaBody audio,
            MediaBody video,
            MediaBody sticker,
            Context context
    ) {

        /** The wamid of the message this one answers: set on button taps and on quoted replies. */
        public String replyTo() {
            return context == null ? null : context.id();
        }

        public MessageContent toContent() {
            final var content = switch (type == null ? "" : type) {
                case "text" -> text == null ? null : new MessageContent.Text(text.body());
                case "button" -> button == null ? null
                        : new MessageContent.Reply(button.payload() == null ? button.text() : button.payload(), button.text());
                case "interactive" -> interactive == null ? null : interactive.toContent();
                case "image" -> MediaBody.toContent("image", image);
                case "document" -> MediaBody.toContent("document", document);
                case "audio" -> MediaBody.toContent("audio", audio);
                case "video" -> MediaBody.toContent("video", video);
                case "sticker" -> MediaBody.toContent("sticker", sticker);
                default -> null;
            };
            return content == null ? new MessageContent.Unsupported(type) : content;
        }
    }

    public record Context(String from, String id) {}

    public record TextBody(String body) {}

    public record ButtonBody(String payload, String text) {}

    public record Interactive(String type, ReplyBody buttonReply, ReplyBody listReply) {
        MessageContent toContent() {
            final var reply = buttonReply != null ? buttonReply : listReply;
            return reply == null ? null : new MessageContent.Reply(reply.id(), reply.title());
        }
    }

    public record ReplyBody(String id, String title, String description) {}

    public record MediaBody(String id, String mimeType, String sha256, String caption, String filename) {
        static MessageContent toContent(final String mediaType, final MediaBody media) {
            return media == null ? null : new MessageContent.Media(mediaType, media.id(), media.mimeType(), media.caption(), media.filename());
        }
    }

    public record Status(String id, String status, String timestamp, String recipientId, List<StatusError> errors) {

        public String errorReason() {
            if (errors == null || errors.isEmpty()) {
                return null;
            }
            final var e = errors.getFirst();
            final var details = e.errorData() == null ? null : e.errorData().details();
            return "[%s] %s%s".formatted(e.code(), e.title(), details == null ? "" : ": " + details);
        }
    }

    public record StatusError(Integer code, String title, String message, ErrorData errorData) {}

    public record ErrorData(String details) {}

    /** Flattens entry → changes → value of the {@code messages} field. */
    public Stream<Value> messageValues() {
        if (entry == null) {
            return Stream.empty();
        }
        return entry.stream()
                .filter(e -> e.changes() != null)
                .flatMap(e -> e.changes().stream())
                .filter(c -> "messages".equals(c.field()) && c.value() != null)
                .map(Change::value);
    }
}
