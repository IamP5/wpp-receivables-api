package com.tubadev.receivables.application.journey;

import com.tubadev.receivables.domain.biometrics.BiometricResult.Rejected;
import com.tubadev.receivables.domain.contract.ContractDocument;
import com.tubadev.receivables.domain.conversation.journey.Intent;
import com.tubadev.receivables.domain.message.MessageContent;
import com.tubadev.receivables.domain.message.MessageContent.Buttons;
import com.tubadev.receivables.domain.message.MessageContent.Buttons.Button;
import com.tubadev.receivables.domain.message.MessageContent.Media;
import com.tubadev.receivables.domain.message.MessageContent.Text;
import com.tubadev.receivables.domain.receivable.AnticipationOffer;
import com.tubadev.receivables.domain.receivable.Money;
import com.tubadev.receivables.domain.utils.InstantUtils;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Copy of every message the bot sends (pt-BR). Kept in one place so product/UX can review it.
 */
public final class JourneyReplies {

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm", PT_BR);

    private static final Button ANTICIPATE = new Button(Intent.ANTICIPATE, "Antecipar boletos");
    private static final Button CONFIRM = new Button(Intent.CONFIRM_OFFER, "Confirmar");
    private static final Button CHANGE = new Button(Intent.CHANGE_AMOUNT, "Alterar valor");
    private static final Button DECLINE = new Button(Intent.DECLINE_OFFER, "Cancelar");

    private JourneyReplies() {
    }

    public static MessageContent menu(final String firstName) {
        return new Buttons("""
                Olá, %s! 👋
                Aqui você antecipa seus boletos e recebe o dinheiro na conta, sem burocracia.
                Como posso ajudar?""".formatted(firstName), List.of(ANTICIPATE));
    }

    public static MessageContent askAmount(final Money available) {
        return new Text("""
                Você tem até *%s* disponíveis para antecipar. 💰
                Quanto você quer receber? Digite o valor, por exemplo: *10 mil* ou *R$ 7.500,00*.""".formatted(money(available)));
    }

    public static MessageContent invalidAmount(final Money available) {
        return new Text("""
                Não consegui entender o valor. 🤔
                Digite quanto quer antecipar (até *%s*), por exemplo: *5 mil* ou *R$ 4.200,00*.""".formatted(money(available)));
    }

    public static MessageContent exceedsAvailable(final Money requested, final Money available) {
        return new Text("""
                O valor de *%s* é maior que o disponível agora.
                Você pode antecipar até *%s*. Qual valor você quer?""".formatted(money(requested), money(available)));
    }

    public static MessageContent belowMinimum(final Money requested, final Money minimum) {
        return new Text("O valor mínimo para antecipação é *%s* (você informou %s). Qual valor você quer?"
                .formatted(money(minimum), money(requested)));
    }

    public static List<MessageContent> offer(final AnticipationOffer offer) {
        final var boletos = offer.receivables().stream()
                .map(r -> "• %s — venc. %s — %s".formatted(r.payerName(), date(r.dueDate()), money(r.amount())))
                .collect(Collectors.joining("\n"));

        final var excess = offer.netAmount().minus(offer.requestedAmount());
        final var intro = excess.amount().signum() == 0
                ? "Para você receber *%s*, separamos estes boletos:".formatted(money(offer.requestedAmount()))
                : """
                Você pediu *%s*. Como cada boleto é antecipado por inteiro, esta é a combinação dos seus boletos \
                que mais se aproxima desse valor:""".formatted(money(offer.requestedAmount()));
        final var received = excess.amount().signum() == 0
                ? "*Você recebe: %s*".formatted(money(offer.netAmount()))
                : "*Você recebe: %s* (%s acima do pedido)".formatted(money(offer.netAmount()), money(excess));

        final var details = new Text("""
                %s
                %s

                Valor dos boletos: %s
                Taxa (%s a.m.): %s
                %s
                Proposta válida até %s.""".formatted(
                intro,
                boletos,
                money(offer.grossAmount()),
                percent(offer.monthlyRate()),
                money(offer.feeAmount()),
                received,
                DATE_TIME.format(offer.validUntil().atZone(InstantUtils.BUSINESS_ZONE))
        ));

        return List.of(details, new Buttons("Confirma a antecipação?", List.of(CONFIRM, CHANGE, DECLINE)));
    }

    public static MessageContent offerReminder() {
        return new Buttons("Para seguir, confirme a proposta acima, altere o valor ou cancele.", List.of(CONFIRM, CHANGE, DECLINE));
    }

    public static MessageContent offerExpired() {
        return new Text("Essa proposta expirou. ⏱️ Calculei de novo com as condições de agora:");
    }

    public static MessageContent staleReply() {
        return new Text("Esse botão é de uma mensagem anterior e não vale mais. Use as opções abaixo. 👇");
    }

    public static MessageContent handoffReleased() {
        return new Text("Seu atendimento foi encerrado. Quando quiser, envie *oi* para ver as opções de novo.");
    }

    public static MessageContent askSelfie() {
        return new Text("""
                Para sua segurança, precisamos confirmar que é você. 📸
                Envie agora uma *selfie*: rosto inteiro e de frente, em um lugar bem iluminado, sem óculos escuros ou boné.""");
    }

    public static MessageContent selfieReminder() {
        return new Buttons("Estamos aguardando sua *selfie* para concluir a antecipação. 📸 Se preferir, altere o valor ou cancele.",
                List.of(CHANGE, DECLINE));
    }

    public static MessageContent selfieNotAnImage() {
        return new Text("Para a validação eu preciso de uma *foto* do seu rosto. Tire uma selfie e envie por aqui. 🤳");
    }

    public static MessageContent selfieDownloadFailed() {
        return new Text("Não consegui receber sua foto. 😕 Pode enviar a selfie novamente?");
    }

    public static MessageContent selfieRejected(final String reason, final int remainingAttempts) {
        final var why = switch (reason) {
            case Rejected.UNSUPPORTED_MEDIA -> "o formato da imagem não é suportado";
            case Rejected.LOW_QUALITY -> "a foto ficou com pouca qualidade (tente em um lugar mais iluminado)";
            case Rejected.NO_FACE_DETECTED -> "não identificamos um rosto na foto";
            case Rejected.FACE_MISMATCH -> "a foto não confere com o seu cadastro";
            case null, default -> "não foi possível validar a foto";
        };
        return new Text("""
                Não conseguimos validar sua selfie: %s.
                Tente novamente (%s).""".formatted(why, remainingAttempts == 1 ? "última tentativa" : remainingAttempts + " tentativas restantes"));
    }

    public static MessageContent biometricsFailed() {
        return new Text("""
                Não conseguimos confirmar sua identidade pela selfie. 🔒
                Vou te transferir para um atendente para concluir com segurança.""");
    }

    public static MessageContent contract(final String mediaId, final ContractDocument document, final String protocol) {
        return new Media("document", mediaId, ContractDocument.MIME_TYPE,
                "📄 Seu contrato de antecipação (protocolo %s). Guarde este documento.".formatted(protocol), document.filename());
    }

    public static MessageContent contractUnavailable(final String protocol) {
        return new Text("Seu contrato (protocolo *%s*) foi gerado. Guarde o número do protocolo.".formatted(protocol));
    }

    public static MessageContent anticipationRequested(final String protocol, final Money netAmount, final LocalDate creditDate) {
        return new Text("""
                Pronto! ✅ Sua antecipação foi solicitada.
                Protocolo: *%s*
                Você vai receber *%s* na sua conta até %s.
                Obrigado por antecipar com a gente!""".formatted(protocol, money(netAmount), date(creditDate)));
    }

    public static MessageContent anticipationRefused(final String reason) {
        return new Text("Não conseguimos concluir essa antecipação (%s). Vamos tentar com outro valor?".formatted(reason));
    }

    public static MessageContent notEligible() {
        return new Text("""
                No momento você não tem boletos disponíveis para antecipação.
                Assim que houver, avisamos por aqui.""");
    }

    public static MessageContent declined() {
        return new Text("Tudo bem, a proposta foi cancelada. Quando quiser antecipar, é só mandar um *oi*. 😉");
    }

    public static MessageContent unknownCustomer() {
        return new Text("""
                Não encontrei um cadastro para este número. 🔎
                Vou te transferir para um atendente que pode te ajudar.""");
    }

    public static MessageContent optedOut() {
        return new Text("Pronto, você não vai mais receber nossas ofertas por aqui. Se mudar de ideia, é só mandar um *oi*.");
    }

    static String money(final Money money) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(money.amount());
    }

    static String percent(final BigDecimal rate) {
        final var format = NumberFormat.getPercentInstance(PT_BR);
        format.setMinimumFractionDigits(2);
        return format.format(rate);
    }

    static String date(final LocalDate date) {
        return DATE.format(date);
    }
}
