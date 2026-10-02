package com.tubadev.receivables.application.journey.impl;

import com.tubadev.receivables.application.contract.IssueContract;
import com.tubadev.receivables.application.journey.AnticipationJourney;
import com.tubadev.receivables.application.journey.JourneyReplies;
import com.tubadev.receivables.domain.biometrics.BiometricResult.Approved;
import com.tubadev.receivables.domain.biometrics.BiometricResult.Rejected;
import com.tubadev.receivables.domain.biometrics.BiometricsGateway;
import com.tubadev.receivables.domain.biometrics.Selfie;
import com.tubadev.receivables.domain.contract.ContractDocument;
import com.tubadev.receivables.domain.contract.ElectronicSignature;
import com.tubadev.receivables.domain.conversation.Conversation;
import com.tubadev.receivables.domain.conversation.ConversationId;
import com.tubadev.receivables.domain.conversation.ConversationCommand.AdvanceTo;
import com.tubadev.receivables.domain.conversation.journey.Intent;
import com.tubadev.receivables.domain.conversation.journey.Intent.ChangeAmount;
import com.tubadev.receivables.domain.conversation.journey.Intent.ConfirmOffer;
import com.tubadev.receivables.domain.conversation.journey.Intent.DeclineOffer;
import com.tubadev.receivables.domain.conversation.journey.Intent.InformAmount;
import com.tubadev.receivables.domain.conversation.journey.Intent.OptOut;
import com.tubadev.receivables.domain.conversation.journey.Intent.SentAttachment;
import com.tubadev.receivables.domain.conversation.journey.Intent.WantsAnticipation;
import com.tubadev.receivables.domain.conversation.journey.JourneyStage;
import com.tubadev.receivables.domain.conversation.journey.JourneyStage.AwaitingAmount;
import com.tubadev.receivables.domain.conversation.journey.JourneyStage.AwaitingSelfie;
import com.tubadev.receivables.domain.conversation.journey.JourneyStage.Closed;
import com.tubadev.receivables.domain.conversation.journey.JourneyStage.Completed;
import com.tubadev.receivables.domain.conversation.journey.JourneyStage.HumanHandoff;
import com.tubadev.receivables.domain.conversation.journey.JourneyStage.MainMenu;
import com.tubadev.receivables.domain.conversation.journey.JourneyStage.ReviewingOffer;
import com.tubadev.receivables.domain.conversation.journey.JourneyStage.Started;
import com.tubadev.receivables.domain.customer.Customer;
import com.tubadev.receivables.domain.customer.CustomerGateway;
import com.tubadev.receivables.domain.message.MediaFile;
import com.tubadev.receivables.domain.message.MediaGateway;
import com.tubadev.receivables.domain.message.MessageContent;
import com.tubadev.receivables.domain.receivable.AnticipationOffer;
import com.tubadev.receivables.domain.receivable.AnticipationGateway;
import com.tubadev.receivables.domain.receivable.AnticipationResult.Refused;
import com.tubadev.receivables.domain.receivable.AnticipationResult.Requested;
import com.tubadev.receivables.domain.receivable.EligibilityGateway;
import com.tubadev.receivables.domain.receivable.Money;
import com.tubadev.receivables.domain.receivable.OfferResult.BelowMinimum;
import com.tubadev.receivables.domain.receivable.OfferResult.ExceedsAvailable;
import com.tubadev.receivables.domain.receivable.OfferResult.NotEligible;
import com.tubadev.receivables.domain.receivable.OfferResult.Offered;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The anticipation flow:
 * <ol>
 *     <li>greeting → menu (anticipate / talk to an agent)</li>
 *     <li>anticipate → eligibility check → ask how much the customer wants to receive</li>
 *     <li>amount → the Receivables service picks the boletos that cover it and prices the offer</li>
 *     <li>confirm → ask for a selfie; change → ask the amount again; cancel → close</li>
 *     <li>selfie → biometric validation (up to 3 attempts, then an agent takes over) → anticipation requested →
 *     contract issued and sent as a PDF document with the protocol</li>
 * </ol>
 * "parar" (opt-out) works from any stage. Typing an amount works from any stage too,
 * so "quero antecipar 10 mil" goes straight to the offer.
 */
public class DefaultAnticipationJourney extends AnticipationJourney {

    static final int MAX_SELFIE_ATTEMPTS = 3;

    private final AnticipationGateway anticipationGateway;
    private final BiometricsGateway biometricsGateway;
    private final CustomerGateway customerGateway;
    private final EligibilityGateway eligibilityGateway;
    private final MediaGateway mediaGateway;
    private final IssueContract issueContract;

    public DefaultAnticipationJourney(
            final AnticipationGateway anticipationGateway,
            final BiometricsGateway biometricsGateway,
            final CustomerGateway customerGateway,
            final EligibilityGateway eligibilityGateway,
            final MediaGateway mediaGateway,
            final IssueContract issueContract
    ) {
        this.anticipationGateway = Objects.requireNonNull(anticipationGateway);
        this.biometricsGateway = Objects.requireNonNull(biometricsGateway);
        this.customerGateway = Objects.requireNonNull(customerGateway);
        this.eligibilityGateway = Objects.requireNonNull(eligibilityGateway);
        this.mediaGateway = Objects.requireNonNull(mediaGateway);
        this.issueContract = Objects.requireNonNull(issueContract);
    }

    private record Turn(JourneyStage stage, Intent intent) {}

    @Override
    public List<MessageContent> handle(final Conversation aConversation, final Optional<Customer> aCustomer, final Intent anIntent) {
        if (aCustomer.isEmpty()) {
            return unknownCustomer(aConversation);
        }

        final var customer = aCustomer.get();

        return switch (new Turn(aConversation.stage(), anIntent)) {
            // an agent is in charge: the bot only records the messages
            case Turn(HumanHandoff _, _) -> List.of();

            case Turn(_, OptOut _) -> optOut(aConversation, customer);
            case Turn(_, InformAmount(var amount)) -> offer(aConversation, customer, amount);

            case Turn(ReviewingOffer(var offerId, var requested, var net), ConfirmOffer _) ->
                    askSelfie(aConversation, new AwaitingSelfie(offerId, requested, net, 0));
            case Turn(ReviewingOffer _, DeclineOffer _), Turn(AwaitingSelfie _, DeclineOffer _) -> decline(aConversation);
            case Turn(AwaitingSelfie stage, SentAttachment attachment) -> selfie(aConversation, customer, stage, attachment);
            case Turn(_, WantsAnticipation _), Turn(_, ChangeAmount _) -> askAmount(aConversation, customer);

            case Turn(AwaitingAmount(var available), _) -> List.of(JourneyReplies.invalidAmount(available));
            case Turn(ReviewingOffer _, _) -> List.of(JourneyReplies.offerReminder());
            case Turn(AwaitingSelfie _, _) -> List.of(JourneyReplies.selfieReminder());
            case Turn(Started _, _), Turn(MainMenu _, _) -> menu(aConversation, customer);

            // terminal conversations are never loaded as open, a new one is started instead
            case Turn(Completed _, _), Turn(Closed _, _) -> List.of();
        };
    }

    private List<MessageContent> menu(final Conversation aConversation, final Customer customer) {
        advance(aConversation, new MainMenu());
        return List.of(JourneyReplies.menu(customer.firstName()));
    }

    private List<MessageContent> askAmount(final Conversation aConversation, final Customer customer) {
        final var eligibility = this.eligibilityGateway.eligibilityOf(customer.id());
        if (!eligibility.eligible()) {
            return notEligible(aConversation);
        }

        advance(aConversation, new AwaitingAmount(eligibility.availableAmount()));
        return List.of(JourneyReplies.askAmount(eligibility.availableAmount()));
    }

    private List<MessageContent> offer(final Conversation aConversation, final Customer customer, final Money requested) {
        return switch (this.anticipationGateway.offerFor(customer.id(), requested)) {
            case Offered(var offer) -> {
                advance(aConversation, new ReviewingOffer(offer.offerId(), offer.requestedAmount(), offer.netAmount()));
                yield JourneyReplies.offer(offer);
            }
            case ExceedsAvailable(var req, var available) -> {
                advance(aConversation, new AwaitingAmount(available));
                yield List.of(JourneyReplies.exceedsAvailable(req, available));
            }
            case BelowMinimum(var req, var minimum) -> {
                final var eligibility = this.eligibilityGateway.eligibilityOf(customer.id());
                advance(aConversation, new AwaitingAmount(eligibility.availableAmount()));
                yield List.of(JourneyReplies.belowMinimum(req, minimum));
            }
            case NotEligible _ -> notEligible(aConversation);
        };
    }

    private List<MessageContent> askSelfie(final Conversation aConversation, final AwaitingSelfie stage) {
        advance(aConversation, stage);
        return List.of(JourneyReplies.askSelfie());
    }

    private List<MessageContent> selfie(
            final Conversation aConversation,
            final Customer customer,
            final AwaitingSelfie stage,
            final SentAttachment attachment
    ) {
        if (!attachment.isImage()) {
            return List.of(JourneyReplies.selfieNotAnImage());
        }

        final var photo = this.mediaGateway.download(attachment.media().mediaId()).filter(file -> file.size() > 0);
        if (photo.isEmpty()) {
            return List.of(JourneyReplies.selfieDownloadFailed());
        }

        final var aSelfie = new Selfie(photo.get().content(), photo.get().mimeType());
        return switch (this.biometricsGateway.verify(customer.id(), aSelfie)) {
            case Approved approved -> request(aConversation, customer, stage.offerId(), approved);
            case Rejected(var reason) -> selfieRejected(aConversation, stage, reason);
        };
    }

    private List<MessageContent> selfieRejected(final Conversation aConversation, final AwaitingSelfie stage, final String reason) {
        final var next = stage.retry();
        if (next.attempts() >= MAX_SELFIE_ATTEMPTS) {
            advance(aConversation, new HumanHandoff("biometrics_failed"));
            return List.of(JourneyReplies.biometricsFailed());
        }

        advance(aConversation, next);
        return List.of(JourneyReplies.selfieRejected(reason, MAX_SELFIE_ATTEMPTS - next.attempts()));
    }

    private List<MessageContent> request(
            final Conversation aConversation,
            final Customer customer,
            final String offerId,
            final Approved biometrics
    ) {
        return switch (this.anticipationGateway.request(customer.id(), offerId)) {
            case Requested(var protocol, var netAmount, var creditDate, var offer) -> {
                final var signature = new ElectronicSignature(ElectronicSignature.WHATSAPP, aConversation.phoneNumber().value(),
                        biometrics.verificationId(), biometrics.score(), biometrics.verifiedAt());
                final var issued = this.issueContract.execute(
                        new IssueContractInput(customer, aConversation.id(), protocol, offer, creditDate, signature));

                advance(aConversation, new Completed(protocol));
                yield List.of(
                        JourneyReplies.anticipationRequested(protocol, netAmount, creditDate),
                        contractMessage(protocol, issued.document())
                );
            }
            case Refused(var reason) -> {
                final var replies = new ArrayList<MessageContent>();
                replies.add(JourneyReplies.anticipationRefused(reason));
                replies.addAll(askAmount(aConversation, customer));
                yield replies;
            }
        };
    }

    private MessageContent contractMessage(final String protocol, final ContractDocument document) {
        return this.mediaGateway.upload(new MediaFile(document.content(), ContractDocument.MIME_TYPE, document.filename()))
                .map(mediaId -> JourneyReplies.contract(mediaId, document, protocol))
                .orElseGet(() -> JourneyReplies.contractUnavailable(protocol));
    }

    private List<MessageContent> decline(final Conversation aConversation) {
        advance(aConversation, new Closed("offer_declined"));
        return List.of(JourneyReplies.declined());
    }

    private List<MessageContent> notEligible(final Conversation aConversation) {
        advance(aConversation, new Closed("not_eligible"));
        return List.of(JourneyReplies.notEligible());
    }

    private List<MessageContent> optOut(final Conversation aConversation, final Customer customer) {
        this.customerGateway.optOut(customer.id());
        advance(aConversation, new Closed("opted_out"));
        return List.of(JourneyReplies.optedOut());
    }

    private List<MessageContent> unknownCustomer(final Conversation aConversation) {
        if (aConversation.stage() instanceof HumanHandoff) {
            return List.of();
        }
        advance(aConversation, new HumanHandoff("unknown_customer"));
        return List.of(JourneyReplies.unknownCustomer());
    }

    private static void advance(final Conversation aConversation, final JourneyStage next) {
        aConversation.execute(new AdvanceTo(next));
    }

    record IssueContractInput(
            Customer customer,
            ConversationId conversationId,
            String protocol,
            AnticipationOffer offer,
            LocalDate expectedCreditDate,
            ElectronicSignature signature
    ) implements IssueContract.Input {
    }
}
