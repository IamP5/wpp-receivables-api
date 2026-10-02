package com.tubadev.receivables.domain.conversation.journey;

import com.tubadev.receivables.domain.message.MessageContent;
import com.tubadev.receivables.domain.message.MessageContent.Buttons;
import com.tubadev.receivables.domain.message.MessageContent.Media;
import com.tubadev.receivables.domain.message.MessageContent.Reply;
import com.tubadev.receivables.domain.message.MessageContent.Template;
import com.tubadev.receivables.domain.message.MessageContent.Text;
import com.tubadev.receivables.domain.message.MessageContent.Unsupported;
import com.tubadev.receivables.domain.receivable.Money;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * What the customer means with an inbound message, independent of how it was sent
 * (typed text, interactive button or template quick-reply button).
 */
public sealed interface Intent {

    /** Reply ids used in interactive buttons and in the quick-reply buttons of campaign templates. */
    String ANTICIPATE = "ANTICIPATE";
    String CONFIRM_OFFER = "CONFIRM_OFFER";
    String CHANGE_AMOUNT = "CHANGE_AMOUNT";
    String DECLINE_OFFER = "DECLINE_OFFER";
    String OPT_OUT = "OPT_OUT";
    String MENU = "MENU";

    record Greeting() implements Intent {}
    record WantsAnticipation() implements Intent {}
    record InformAmount(Money amount) implements Intent {}
    record ConfirmOffer() implements Intent {}
    record ChangeAmount() implements Intent {}
    record DeclineOffer() implements Intent {}
    record OptOut() implements Intent {}
    record SentAttachment(Media media) implements Intent {
        public boolean isImage() {
            return "image".equals(media.mediaType());
        }
    }
    record FreeText(String text) implements Intent {}
    record Unrecognized() implements Intent {}
    /**
     * A tap on a button that no longer applies: it belongs to an earlier session, or a newer message offered the same
     * button (e.g. "Confirmar" on an offer that was replaced by another amount).
     */
    record StaleReply(Intent intended) implements Intent {}

    /**
     * Buttons whose effect depends on the message they came from. The others (anticipate, menu, opt-out) mean the same
     * thing whenever they are tapped, so old campaign and menu buttons keep working.
     */
    default boolean dependsOnContext() {
        return this instanceof ConfirmOffer || this instanceof DeclineOffer;
    }

    static Intent of(final MessageContent content) {
        return switch (content) {
            case Reply(var id, var title) -> fromReply(id, title);
            case Text(var body) -> fromText(body);
            case Media m -> new SentAttachment(m);
            case Template _, Buttons _, Unsupported _ -> new Unrecognized();
        };
    }

    private static Intent fromReply(final String id, final String title) {
        return switch (id.toUpperCase(Locale.ROOT)) {
            case ANTICIPATE -> new WantsAnticipation();
            case CONFIRM_OFFER -> new ConfirmOffer();
            case CHANGE_AMOUNT -> new ChangeAmount();
            case DECLINE_OFFER -> new DeclineOffer();
            case OPT_OUT -> new OptOut();
            case MENU -> new Greeting();
            // template quick-replies configured in Meta without a known payload: fall back to the button text
            default -> title == null ? new Unrecognized() : fromText(title);
        };
    }

    private static Intent fromText(final String body) {
        final var text = normalize(body);

        if (Keywords.OPT_OUT.contains(text)) return new OptOut();
        if (Keywords.containsAny(text, Keywords.CHANGE)) return new ChangeAmount();
        if (Keywords.DECLINE.contains(text)) return new DeclineOffer();
        if (Keywords.CONFIRM.contains(text)) return new ConfirmOffer();

        final var amount = AmountParser.parse(text);
        if (amount.isPresent()) return new InformAmount(amount.get());

        if (Keywords.containsAny(text, Keywords.ANTICIPATE)) return new WantsAnticipation();
        if (Keywords.GREETING.contains(text)) return new Greeting();
        return new FreeText(body);
    }

    static String normalize(final String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[!?]+", "")
                .replaceAll("\\s+", " ")
                .strip()
                .replaceAll("\\.$", "");
    }

    final class Keywords {
        static final Set<String> OPT_OUT = Set.of("parar", "sair", "stop", "descadastrar", "nao quero mais receber");
        static final Set<String> CHANGE = Set.of("alterar valor", "outro valor", "mudar valor", "mudar o valor", "trocar valor");
        static final Set<String> DECLINE = Set.of("cancelar", "nao", "desistir", "nao quero");
        static final Set<String> CONFIRM = Set.of("confirmar", "confirmo", "sim", "aceito", "pode seguir", "ok");
        static final Set<String> ANTICIPATE = Set.of("antecip", "simular", "simulacao", "adiantar");
        static final Set<String> GREETING = Set.of("oi", "ola", "menu", "inicio", "comecar", "bom dia", "boa tarde", "boa noite");

        private Keywords() {
        }

        static boolean containsAny(final String text, final Set<String> fragments) {
            return fragments.stream().anyMatch(text::contains);
        }
    }
}
