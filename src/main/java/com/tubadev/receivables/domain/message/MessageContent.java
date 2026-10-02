package com.tubadev.receivables.domain.message;

import com.tubadev.receivables.domain.AssertionConcern;

import java.util.List;

/**
 * Every kind of payload exchanged with the customer. New WhatsApp message types are added as new records,
 * and the compiler points at every exhaustive {@code switch} that must handle them.
 */
public sealed interface MessageContent extends AssertionConcern {

    String TEXT = "text";
    String TEMPLATE = "template";
    String BUTTONS = "buttons";
    String REPLY = "reply";
    String MEDIA = "media";
    String UNSUPPORTED = "unsupported";

    default String type() {
        return switch (this) {
            case Text _ -> TEXT;
            case Template _ -> TEMPLATE;
            case Buttons _ -> BUTTONS;
            case Reply _ -> REPLY;
            case Media _ -> MEDIA;
            case Unsupported _ -> UNSUPPORTED;
        };
    }

    /** Business-initiated messages outside the 24h customer service window must be approved templates. */
    default boolean requiresServiceWindow() {
        return !(this instanceof Template);
    }

    static Class<? extends MessageContent> typeOf(final String type) {
        return switch (type) {
            case TEXT -> Text.class;
            case TEMPLATE -> Template.class;
            case BUTTONS -> Buttons.class;
            case REPLY -> Reply.class;
            case MEDIA -> Media.class;
            default -> Unsupported.class;
        };
    }

    record Text(String body) implements MessageContent {
        public Text {
            this.assertArgumentNotEmpty(body, "'body' should not be empty");
            this.assertArgumentMaxLength(body, 4096, "'body' should not exceed 4096 characters");
        }
    }

    record Template(String name, String languageCode, List<String> bodyParameters) implements MessageContent {
        public Template {
            this.assertArgumentNotEmpty(name, "'template.name' should not be empty");
            this.assertArgumentNotEmpty(languageCode, "'template.languageCode' should not be empty");
            bodyParameters = bodyParameters == null ? List.of() : List.copyOf(bodyParameters);
        }
    }

    record Buttons(String body, List<Button> buttons) implements MessageContent {
        public Buttons {
            this.assertArgumentNotEmpty(body, "'body' should not be empty");
            this.assertArgumentNotEmpty(buttons, "'buttons' should not be empty");
            this.assertConditionTrue(buttons.size() <= 3, "WhatsApp supports at most 3 reply buttons");
            buttons = List.copyOf(buttons);
        }

        public record Button(String id, String title) implements AssertionConcern {
            public Button {
                this.assertArgumentNotEmpty(id, "'button.id' should not be empty");
                this.assertArgumentNotEmpty(title, "'button.title' should not be empty");
                this.assertArgumentMaxLength(title, 20, "'button.title' should not exceed 20 characters");
            }
        }
    }

    /** Inbound answer to an interactive button or to a template quick-reply button. */
    record Reply(String id, String title) implements MessageContent {
        public Reply {
            this.assertArgumentNotEmpty(id, "'reply.id' should not be empty");
        }
    }

    record Media(String mediaType, String mediaId, String mimeType, String caption, String filename) implements MessageContent {
        public Media {
            this.assertArgumentNotEmpty(mediaType, "'media.mediaType' should not be empty");
            this.assertArgumentNotEmpty(mediaId, "'media.mediaId' should not be empty");
        }
    }

    record Unsupported(String originalType) implements MessageContent {}
}
