package com.tubadev.receivables.domain.conversation.journey;

import com.tubadev.receivables.domain.UnitTest;
import com.tubadev.receivables.domain.message.MessageContent;
import com.tubadev.receivables.domain.receivable.Money;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IntentTest extends UnitTest {

    @ParameterizedTest
    @CsvSource({
            "Oi!, Greeting",
            "Olá, Greeting",
            "menu, Greeting",
            "Quero antecipar, WantsAnticipation",
            "simular, WantsAnticipation",
            "PARAR, OptOut",
            "sim, ConfirmOffer",
            "Confirmo, ConfirmOffer",
            "não, DeclineOffer",
            "cancelar, DeclineOffer",
            "outro valor, ChangeAmount",
            "qual o horário de vocês, FreeText",
    })
    void givenText_whenResolveIntent_shouldUnderstandIt(final String text, final String expected) {
        Assertions.assertEquals(expected, Intent.of(new MessageContent.Text(text)).getClass().getSimpleName());
    }

    @Test
    void givenTextWithAmount_whenResolveIntent_shouldInformAmount() {
        final var intent = Intent.of(new MessageContent.Text("quero antecipar 12 mil"));
        Assertions.assertEquals(new Intent.InformAmount(Money.brl("12000")), intent);
    }

    @Test
    void givenInteractiveReply_whenResolveIntent_shouldUseReplyId() {
        Assertions.assertInstanceOf(Intent.ConfirmOffer.class, Intent.of(new MessageContent.Reply(Intent.CONFIRM_OFFER, "Confirmar")));
        Assertions.assertInstanceOf(Intent.WantsAnticipation.class, Intent.of(new MessageContent.Reply(Intent.ANTICIPATE, "Antecipar boletos")));
    }

    @Test
    void givenTemplateQuickReplyWithUnknownPayload_whenResolveIntent_shouldFallbackToTitle() {
        Assertions.assertInstanceOf(Intent.WantsAnticipation.class, Intent.of(new MessageContent.Reply("Quero antecipar", "Quero antecipar")));
    }

    @Test
    void givenMedia_whenResolveIntent_shouldBeAttachment() {
        final var media = new MessageContent.Media("document", "media-1", "application/pdf", null, "boleto.pdf");
        final var intent = Assertions.assertInstanceOf(Intent.SentAttachment.class, Intent.of(media));
        Assertions.assertEquals(media, intent.media());
        Assertions.assertFalse(intent.isImage());
        Assertions.assertTrue(((Intent.SentAttachment) Intent.of(new MessageContent.Media("image", "media-2", "image/jpeg", null, null))).isImage());
    }
}
