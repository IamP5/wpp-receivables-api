package com.tubadev.receivables.infrastructure.gateway.client.whatsapp.models;

import com.tubadev.receivables.domain.exceptions.DomainException;
import com.tubadev.receivables.domain.message.MessageContent;

import java.util.List;

/**
 * Body of {@code POST /{version}/{phone-number-id}/messages}.
 * See https://developers.facebook.com/docs/whatsapp/cloud-api/reference/messages
 */
public record WhatsAppMessageRequest(
        String messagingProduct,
        String recipientType,
        String to,
        String type,
        Text text,
        Template template,
        Interactive interactive,
        MediaObject image,
        MediaObject document
) {

    private static final String WHATSAPP = "whatsapp";
    private static final String INDIVIDUAL = "individual";

    public record Text(boolean previewUrl, String body) {}

    public record Template(String name, Language language, List<Component> components) {}
    public record Language(String code) {}
    public record Component(String type, List<Parameter> parameters) {}
    public record Parameter(String type, String text) {}

    public record Interactive(String type, Body body, Action action) {}
    public record Body(String text) {}
    public record Action(List<Button> buttons) {}
    public record Button(String type, Reply reply) {}
    public record Reply(String id, String title) {}

    /** Media previously uploaded with {@code POST /{phone-number-id}/media}. */
    public record MediaObject(String id, String caption, String filename) {}

    public static WhatsAppMessageRequest of(final String to, final MessageContent content) {
        return switch (content) {
            case MessageContent.Text(var body) ->
                    new WhatsAppMessageRequest(WHATSAPP, INDIVIDUAL, to, "text", new Text(false, body), null, null, null, null);

            case MessageContent.Template(var name, var languageCode, var params) -> {
                final var components = params.isEmpty() ? null : List.of(new Component("body",
                        params.stream().map(p -> new Parameter("text", p)).toList()));
                yield new WhatsAppMessageRequest(WHATSAPP, INDIVIDUAL, to, "template", null,
                        new Template(name, new Language(languageCode), components), null, null, null);
            }

            case MessageContent.Buttons(var body, var buttons) -> {
                final var action = new Action(buttons.stream()
                        .map(b -> new Button("reply", new Reply(b.id(), b.title())))
                        .toList());
                yield new WhatsAppMessageRequest(WHATSAPP, INDIVIDUAL, to, "interactive", null, null,
                        new Interactive("button", new Body(body), action), null, null);
            }

            case MessageContent.Media(var mediaType, var mediaId, _, var caption, var filename) -> switch (mediaType) {
                case "document" -> new WhatsAppMessageRequest(WHATSAPP, INDIVIDUAL, to, "document", null, null, null,
                        null, new MediaObject(mediaId, caption, filename));
                case "image" -> new WhatsAppMessageRequest(WHATSAPP, INDIVIDUAL, to, "image", null, null, null,
                        new MediaObject(mediaId, caption, null), null);
                default -> throw DomainException.with("Media of type %s can´t be sent".formatted(mediaType));
            };

            case MessageContent.Reply _, MessageContent.Unsupported _ ->
                    throw DomainException.with("Content of type %s can´t be sent".formatted(content.type()));
        };
    }
}
