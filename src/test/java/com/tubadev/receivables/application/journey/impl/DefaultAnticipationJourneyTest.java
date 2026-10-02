package com.tubadev.receivables.application.journey.impl;

import com.tubadev.receivables.application.UseCaseTest;
import com.tubadev.receivables.application.contract.IssueContract;
import com.tubadev.receivables.domain.Fixture;
import com.tubadev.receivables.domain.biometrics.BiometricResult;
import com.tubadev.receivables.domain.biometrics.BiometricsGateway;
import com.tubadev.receivables.domain.contract.ContractDocument;
import com.tubadev.receivables.domain.contract.ContractId;
import com.tubadev.receivables.domain.conversation.Conversation;
import com.tubadev.receivables.domain.conversation.ConversationCommand.AdvanceTo;
import com.tubadev.receivables.domain.conversation.journey.Intent;
import com.tubadev.receivables.domain.conversation.journey.JourneyStage;
import com.tubadev.receivables.domain.customer.Customer;
import com.tubadev.receivables.domain.customer.CustomerGateway;
import com.tubadev.receivables.domain.message.MediaFile;
import com.tubadev.receivables.domain.message.MediaGateway;
import com.tubadev.receivables.domain.message.MessageContent;
import com.tubadev.receivables.domain.receivable.AnticipationGateway;
import com.tubadev.receivables.domain.receivable.AnticipationResult;
import com.tubadev.receivables.domain.receivable.Eligibility;
import com.tubadev.receivables.domain.receivable.EligibilityGateway;
import com.tubadev.receivables.domain.receivable.Money;
import com.tubadev.receivables.domain.receivable.OfferResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DefaultAnticipationJourneyTest extends UseCaseTest {

    @Mock
    private AnticipationGateway anticipationGateway;

    @Mock
    private BiometricsGateway biometricsGateway;

    @Mock
    private MediaGateway mediaGateway;

    @Mock
    private IssueContract issueContract;

    @Mock
    private CustomerGateway customerGateway;

    @Mock
    private EligibilityGateway eligibilityGateway;

    @InjectMocks
    private DefaultAnticipationJourney target;

    private Customer maria;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        maria = Fixture.Customers.maria();
        conversation = Fixture.Conversations.of(maria);
    }

    @Test
    void givenGreeting_whenStarted_shouldShowMenu() {
        final var replies = target.handle(conversation, Optional.of(maria), new Intent.Greeting());

        Assertions.assertInstanceOf(JourneyStage.MainMenu.class, conversation.stage());
        final var menu = Assertions.assertInstanceOf(MessageContent.Buttons.class, replies.getFirst());
        Assertions.assertTrue(menu.body().contains("Maria"));
        Assertions.assertEquals(Intent.ANTICIPATE, menu.buttons().getFirst().id());
    }

    @Test
    void givenWantsAnticipation_whenEligible_shouldAskAmountWithAvailableLimit() {
        when(eligibilityGateway.eligibilityOf(maria.id())).thenReturn(Eligibility.of(Money.brl("15000"), 3));

        final var replies = target.handle(conversation, Optional.of(maria), new Intent.WantsAnticipation());

        Assertions.assertEquals(new JourneyStage.AwaitingAmount(Money.brl("15000")), conversation.stage());
        final var text = Assertions.assertInstanceOf(MessageContent.Text.class, replies.getFirst());
        Assertions.assertTrue(text.body().contains("15.000,00"));
    }

    @Test
    void givenWantsAnticipation_whenNotEligible_shouldCloseConversation() {
        when(eligibilityGateway.eligibilityOf(maria.id())).thenReturn(Eligibility.notEligible("no_eligible_receivables"));

        target.handle(conversation, Optional.of(maria), new Intent.WantsAnticipation());

        Assertions.assertEquals(new JourneyStage.Closed("not_eligible"), conversation.stage());
    }

    @Test
    void givenAmount_whenOffered_shouldPresentBoletosAndAskConfirmation() {
        final var offer = Fixture.Offers.tenThousand();
        conversation.execute(new AdvanceTo(new JourneyStage.AwaitingAmount(Money.brl("15000"))));
        when(anticipationGateway.offerFor(maria.id(), Money.brl("10000"))).thenReturn(new OfferResult.Offered(offer));

        final var replies = target.handle(conversation, Optional.of(maria), new Intent.InformAmount(Money.brl("10000")));

        Assertions.assertEquals(new JourneyStage.ReviewingOffer("off_123", Money.brl("10000"), Money.brl("10246.27")), conversation.stage());
        Assertions.assertEquals(2, replies.size());
        final var details = Assertions.assertInstanceOf(MessageContent.Text.class, replies.getFirst());
        Assertions.assertTrue(details.body().contains("Mercado A"));
        Assertions.assertTrue(details.body().contains("10.246,27"));
        final var confirm = Assertions.assertInstanceOf(MessageContent.Buttons.class, replies.getLast());
        Assertions.assertEquals(Intent.CONFIRM_OFFER, confirm.buttons().getFirst().id());
    }

    @Test
    void givenAmountTypedInMenu_whenOffered_shouldSkipAmountQuestion() {
        conversation.execute(new AdvanceTo(new JourneyStage.MainMenu()));
        when(anticipationGateway.offerFor(any(), any())).thenReturn(new OfferResult.Offered(Fixture.Offers.tenThousand()));

        target.handle(conversation, Optional.of(maria), new Intent.InformAmount(Money.brl("10000")));

        Assertions.assertInstanceOf(JourneyStage.ReviewingOffer.class, conversation.stage());
        verifyNoInteractions(eligibilityGateway);
    }

    @Test
    void givenAmountAboveAvailable_shouldAskAgainWithAvailableAmount() {
        conversation.execute(new AdvanceTo(new JourneyStage.AwaitingAmount(Money.brl("15000"))));
        when(anticipationGateway.offerFor(any(), any()))
                .thenReturn(new OfferResult.ExceedsAvailable(Money.brl("50000"), Money.brl("15000")));

        final var replies = target.handle(conversation, Optional.of(maria), new Intent.InformAmount(Money.brl("50000")));

        Assertions.assertEquals(new JourneyStage.AwaitingAmount(Money.brl("15000")), conversation.stage());
        Assertions.assertTrue(((MessageContent.Text) replies.getFirst()).body().contains("50.000,00"));
    }

    @Test
    void givenGibberish_whenAwaitingAmount_shouldExplainHowToTypeIt() {
        conversation.execute(new AdvanceTo(new JourneyStage.AwaitingAmount(Money.brl("15000"))));

        final var replies = target.handle(conversation, Optional.of(maria), new Intent.FreeText("hmm"));

        Assertions.assertInstanceOf(JourneyStage.AwaitingAmount.class, conversation.stage());
        Assertions.assertTrue(((MessageContent.Text) replies.getFirst()).body().contains("Não consegui entender o valor"));
        verifyNoInteractions(anticipationGateway);
    }

    @Test
    void givenConfirm_whenReviewing_shouldAskForSelfie() {
        reviewing();

        final var replies = target.handle(conversation, Optional.of(maria), new Intent.ConfirmOffer());

        Assertions.assertEquals(new JourneyStage.AwaitingSelfie("off_123", Money.brl("10000"), Money.brl("10246.27"), 0),
                conversation.stage());
        Assertions.assertTrue(((MessageContent.Text) replies.getFirst()).body().contains("selfie"));
        verify(anticipationGateway, never()).request(any(), any());
    }

    @Test
    void givenApprovedSelfie_shouldRequestAnticipationAndSendTheContract() {
        awaitingSelfie(0);
        final var offer = Fixture.Offers.tenThousand();
        final var verifiedAt = Instant.parse("2026-10-01T15:00:00Z");
        when(mediaGateway.download("selfie-1")).thenReturn(Optional.of(new MediaFile(new byte[20_000], "image/jpeg", null)));
        when(biometricsGateway.verify(eq(maria.id()), any()))
                .thenReturn(new BiometricResult.Approved("bio_1", new BigDecimal("0.97"), verifiedAt));
        when(anticipationGateway.request(maria.id(), "off_123"))
                .thenReturn(new AnticipationResult.Requested("ANT-20261001-ABC123", Money.brl("10246.27"), LocalDate.of(2026, 10, 2), offer));
        when(issueContract.execute(any())).thenReturn(new Issued(new ContractId("ctr_1"),
                new ContractDocument("%PDF-1.7".getBytes(), "contrato-ANT-20261001-ABC123.pdf")));
        when(mediaGateway.upload(any())).thenReturn(Optional.of("media-pdf-1"));

        final var replies = target.handle(conversation, Optional.of(maria), selfie("selfie-1"));

        Assertions.assertEquals(new JourneyStage.Completed("ANT-20261001-ABC123"), conversation.stage());
        Assertions.assertFalse(conversation.isOpen());
        Assertions.assertEquals(2, replies.size());
        Assertions.assertTrue(((MessageContent.Text) replies.getFirst()).body().contains("ANT-20261001-ABC123"));
        final var contract = Assertions.assertInstanceOf(MessageContent.Media.class, replies.getLast());
        Assertions.assertEquals("document", contract.mediaType());
        Assertions.assertEquals("media-pdf-1", contract.mediaId());
        Assertions.assertEquals("contrato-ANT-20261001-ABC123.pdf", contract.filename());

        final var input = ArgumentCaptor.forClass(IssueContract.Input.class);
        verify(issueContract).execute(input.capture());
        Assertions.assertEquals(offer, input.getValue().offer());
        Assertions.assertEquals("bio_1", input.getValue().signature().biometricVerificationId());
        Assertions.assertEquals(maria.phoneNumber().value(), input.getValue().signature().phoneNumber());
        Assertions.assertEquals(verifiedAt, input.getValue().signature().signedAt());
    }

    @Test
    void givenApprovedSelfie_whenUploadFails_shouldCompleteAndTellTheContractWasIssued() {
        awaitingSelfie(0);
        when(mediaGateway.download(any())).thenReturn(Optional.of(new MediaFile(new byte[20_000], "image/jpeg", null)));
        when(biometricsGateway.verify(any(), any())).thenReturn(new BiometricResult.Approved("bio_1", BigDecimal.ONE, Instant.now()));
        when(anticipationGateway.request(any(), any())).thenReturn(new AnticipationResult.Requested("ANT-1", Money.brl("10246.27"),
                LocalDate.of(2026, 10, 2), Fixture.Offers.tenThousand()));
        when(issueContract.execute(any())).thenReturn(new Issued(new ContractId("ctr_1"), new ContractDocument(new byte[]{1}, "c.pdf")));
        when(mediaGateway.upload(any())).thenReturn(Optional.empty());

        final var replies = target.handle(conversation, Optional.of(maria), selfie("selfie-1"));

        Assertions.assertEquals(new JourneyStage.Completed("ANT-1"), conversation.stage());
        Assertions.assertTrue(((MessageContent.Text) replies.getLast()).body().contains("foi gerado"));
    }

    @Test
    void givenApprovedSelfie_whenRefused_shouldExplainAndAskAmountAgain() {
        awaitingSelfie(0);
        when(mediaGateway.download(any())).thenReturn(Optional.of(new MediaFile(new byte[20_000], "image/jpeg", null)));
        when(biometricsGateway.verify(any(), any())).thenReturn(new BiometricResult.Approved("bio_1", BigDecimal.ONE, Instant.now()));
        when(anticipationGateway.request(any(), any())).thenReturn(new AnticipationResult.Refused("a proposta expirou"));
        when(eligibilityGateway.eligibilityOf(maria.id())).thenReturn(Eligibility.of(Money.brl("9000"), 2));

        final var replies = target.handle(conversation, Optional.of(maria), selfie("selfie-1"));

        Assertions.assertEquals(new JourneyStage.AwaitingAmount(Money.brl("9000")), conversation.stage());
        Assertions.assertEquals(2, replies.size());
        Assertions.assertTrue(((MessageContent.Text) replies.getFirst()).body().contains("a proposta expirou"));
        verifyNoInteractions(issueContract);
    }

    @Test
    void givenRejectedSelfie_shouldAskAgainWithRemainingAttempts() {
        awaitingSelfie(0);
        when(mediaGateway.download(any())).thenReturn(Optional.of(new MediaFile(new byte[500], "image/jpeg", null)));
        when(biometricsGateway.verify(any(), any())).thenReturn(new BiometricResult.Rejected(BiometricResult.Rejected.LOW_QUALITY));

        final var replies = target.handle(conversation, Optional.of(maria), selfie("selfie-1"));

        Assertions.assertEquals(new JourneyStage.AwaitingSelfie("off_123", Money.brl("10000"), Money.brl("10246.27"), 1),
                conversation.stage());
        final var text = ((MessageContent.Text) replies.getFirst()).body();
        Assertions.assertTrue(text.contains("pouca qualidade"));
        Assertions.assertTrue(text.contains("2 tentativas restantes"));
        verifyNoInteractions(anticipationGateway);
    }

    @Test
    void givenLastRejectedSelfie_shouldHandoffToAgent() {
        awaitingSelfie(DefaultAnticipationJourney.MAX_SELFIE_ATTEMPTS - 1);
        when(mediaGateway.download(any())).thenReturn(Optional.of(new MediaFile(new byte[20_000], "image/jpeg", null)));
        when(biometricsGateway.verify(any(), any())).thenReturn(new BiometricResult.Rejected(BiometricResult.Rejected.FACE_MISMATCH));

        target.handle(conversation, Optional.of(maria), selfie("selfie-3"));

        Assertions.assertEquals(new JourneyStage.HumanHandoff("biometrics_failed"), conversation.stage());
        verifyNoInteractions(anticipationGateway);
    }

    @Test
    void givenDocumentInsteadOfSelfie_shouldAskForAPhoto() {
        awaitingSelfie(0);

        final var replies = target.handle(conversation, Optional.of(maria),
                new Intent.SentAttachment(new MessageContent.Media("document", "doc-1", "application/pdf", null, "rg.pdf")));

        Assertions.assertInstanceOf(JourneyStage.AwaitingSelfie.class, conversation.stage());
        Assertions.assertTrue(((MessageContent.Text) replies.getFirst()).body().contains("foto"));
        verifyNoInteractions(mediaGateway, biometricsGateway);
    }

    @Test
    void givenExpiredMedia_shouldAskToSendTheSelfieAgain() {
        awaitingSelfie(0);
        when(mediaGateway.download("selfie-1")).thenReturn(Optional.empty());

        final var replies = target.handle(conversation, Optional.of(maria), selfie("selfie-1"));

        Assertions.assertEquals(0, ((JourneyStage.AwaitingSelfie) conversation.stage()).attempts());
        Assertions.assertTrue(((MessageContent.Text) replies.getFirst()).body().contains("novamente"));
        verifyNoInteractions(biometricsGateway);
    }

    @Test
    void givenText_whenAwaitingSelfie_shouldRemind() {
        awaitingSelfie(0);

        final var replies = target.handle(conversation, Optional.of(maria), new Intent.FreeText("e agora?"));

        Assertions.assertInstanceOf(JourneyStage.AwaitingSelfie.class, conversation.stage());
        Assertions.assertInstanceOf(MessageContent.Buttons.class, replies.getFirst());
    }

    @Test
    void givenDecline_whenAwaitingSelfie_shouldClose() {
        awaitingSelfie(1);

        target.handle(conversation, Optional.of(maria), new Intent.DeclineOffer());

        Assertions.assertEquals(new JourneyStage.Closed("offer_declined"), conversation.stage());
    }

    @Test
    void givenDecline_whenReviewing_shouldClose() {
        reviewing();

        target.handle(conversation, Optional.of(maria), new Intent.DeclineOffer());

        Assertions.assertEquals(new JourneyStage.Closed("offer_declined"), conversation.stage());
    }

    @Test
    void givenOptOut_fromAnyStage_shouldOptOutCustomerAndClose() {
        reviewing();

        target.handle(conversation, Optional.of(maria), new Intent.OptOut());

        verify(customerGateway).optOut(maria.id());
        Assertions.assertEquals(new JourneyStage.Closed("opted_out"), conversation.stage());
    }

    @Test
    void givenHumanHandoff_shouldStaySilent() {
        conversation.execute(new AdvanceTo(new JourneyStage.HumanHandoff("customer_request")));

        final var replies = target.handle(conversation, Optional.of(maria), new Intent.InformAmount(Money.brl("1000")));

        Assertions.assertTrue(replies.isEmpty());
        verifyNoInteractions(anticipationGateway, eligibilityGateway, customerGateway);
    }

    @Test
    void givenUnknownPhoneNumber_shouldHandoffToAgent() {
        final var replies = target.handle(conversation, Optional.empty(), new Intent.Greeting());

        Assertions.assertEquals(new JourneyStage.HumanHandoff("unknown_customer"), conversation.stage());
        Assertions.assertEquals(1, replies.size());
    }

    private void awaitingSelfie(final int attempts) {
        reviewing();
        conversation.execute(new AdvanceTo(new JourneyStage.AwaitingSelfie("off_123", Money.brl("10000"), Money.brl("10246.27"), 0)));
        for (var i = 0; i < attempts; i++) {
            conversation.execute(new AdvanceTo(((JourneyStage.AwaitingSelfie) conversation.stage()).retry()));
        }
    }

    private static Intent selfie(final String mediaId) {
        return new Intent.SentAttachment(new MessageContent.Media("image", mediaId, "image/jpeg", null, null));
    }

    record Issued(ContractId contractId, ContractDocument document) implements IssueContract.Output {
    }

    private void reviewing() {
        conversation.execute(new AdvanceTo(new JourneyStage.AwaitingAmount(Money.brl("15000"))));
        conversation.execute(new AdvanceTo(new JourneyStage.ReviewingOffer("off_123", Money.brl("10000"), Money.brl("10246.27"))));
        verify(anticipationGateway, never()).request(any(), eq("off_123"));
    }
}
