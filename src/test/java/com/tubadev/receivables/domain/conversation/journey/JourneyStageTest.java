package com.tubadev.receivables.domain.conversation.journey;

import com.tubadev.receivables.domain.UnitTest;
import com.tubadev.receivables.domain.conversation.journey.JourneyStage.*;
import com.tubadev.receivables.domain.receivable.Money;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class JourneyStageTest extends UnitTest {

    private static final Money AMOUNT = Money.brl("1000");

    @Test
    void givenHappyPath_whenAdvancing_shouldAllowEveryStep() {
        Assertions.assertTrue(new Started().canAdvanceTo(new MainMenu()));
        Assertions.assertTrue(new MainMenu().canAdvanceTo(new AwaitingAmount(AMOUNT)));
        Assertions.assertTrue(new AwaitingAmount(AMOUNT).canAdvanceTo(new ReviewingOffer("off", AMOUNT, AMOUNT)));
        Assertions.assertTrue(new ReviewingOffer("off", AMOUNT, AMOUNT).canAdvanceTo(new AwaitingSelfie("off", AMOUNT, AMOUNT, 0)));
        Assertions.assertTrue(new AwaitingSelfie("off", AMOUNT, AMOUNT, 0).canAdvanceTo(new Completed("ANT-1")));
    }

    @Test
    void givenStageBeforeBiometrics_whenCompleting_shouldReject() {
        Assertions.assertFalse(new MainMenu().canAdvanceTo(new Completed("ANT-1")));
        Assertions.assertFalse(new AwaitingAmount(AMOUNT).canAdvanceTo(new Completed("ANT-1")));
        Assertions.assertFalse(new ReviewingOffer("off", AMOUNT, AMOUNT).canAdvanceTo(new Completed("ANT-1")));
        Assertions.assertFalse(new MainMenu().canAdvanceTo(new AwaitingSelfie("off", AMOUNT, AMOUNT, 0)));
    }

    @Test
    void givenRejectedSelfie_whenRetrying_shouldCountAttempts() {
        final var stage = new AwaitingSelfie("off", AMOUNT, AMOUNT, 0);
        final var retry = stage.retry();

        Assertions.assertEquals(1, retry.attempts());
        Assertions.assertTrue(stage.canAdvanceTo(retry));
        Assertions.assertTrue(retry.canAdvanceTo(new ReviewingOffer("off-2", AMOUNT, AMOUNT)));
    }

    @Test
    void givenTerminalStage_whenAdvancing_shouldReject() {
        Assertions.assertFalse(new Completed("ANT-1").canAdvanceTo(new MainMenu()));
        Assertions.assertFalse(new Closed("opted_out").canAdvanceTo(new HumanHandoff("x")));
    }

    @Test
    void givenHumanHandoff_whenAskingAmount_shouldRejectUntilBackToMenu() {
        Assertions.assertFalse(new HumanHandoff("x").canAdvanceTo(new AwaitingAmount(AMOUNT)));
        Assertions.assertTrue(new HumanHandoff("x").canAdvanceTo(new MainMenu()));
    }

    @Test
    void givenStoredValue_whenResolvingType_shouldMapEveryStage() {
        Assertions.assertEquals(ReviewingOffer.class, JourneyStage.typeOf(JourneyStage.REVIEWING_OFFER));
        Assertions.assertEquals(AwaitingSelfie.class, JourneyStage.typeOf(JourneyStage.AWAITING_SELFIE));
        Assertions.assertEquals(JourneyStage.AWAITING_AMOUNT, new AwaitingAmount(AMOUNT).value());
    }
}
