package com.tubadev.receivables.domain.conversation.journey;

import com.tubadev.receivables.domain.exceptions.DomainException;
import com.tubadev.receivables.domain.receivable.Money;

import java.time.Instant;

/**
 * Stages of the receivables anticipation journey on WhatsApp:
 * <pre>
 *  STARTED ─▶ MAIN_MENU ─▶ AWAITING_AMOUNT ─▶ REVIEWING_OFFER ─▶ AWAITING_SELFIE ─▶ COMPLETED
 *     │           │              ▲   │              │               ▲  │ (retry)
 *     └───────────┴──────────────┴───┴──────────────┴───────────────┴──┴──▶ HUMAN_HANDOFF / CLOSED
 * </pre>
 * Stages are immutable records carrying the data the next step needs (state pattern with
 * data). {@link #canAdvanceTo(JourneyStage)} guards the transitions. Any open stage closes by inactivity.
 */
public sealed interface JourneyStage {

    String STARTED = "started";
    String MAIN_MENU = "main_menu";
    String AWAITING_AMOUNT = "awaiting_amount";
    String REVIEWING_OFFER = "reviewing_offer";
    String AWAITING_SELFIE = "awaiting_selfie";
    String HUMAN_HANDOFF = "human_handoff";
    String COMPLETED = "completed";
    String CLOSED = "closed";

    /** Conversation just opened (inbound "oi" or a campaign template was delivered). */
    record Started() implements JourneyStage {}

    /** Menu presented: anticipate boletos. */
    record MainMenu() implements JourneyStage {}

    /** We asked how much the customer wants to anticipate. */
    record AwaitingAmount(Money availableAmount) implements JourneyStage {}

    /**
     * An offer covering the requested amount was presented and awaits confirmation.
     * {@code validUntil} is null for sessions stored before it existed.
     */
    record ReviewingOffer(String offerId, Money requestedAmount, Money netAmount, Instant validUntil) implements JourneyStage {

        public boolean isExpired(final Instant now) {
            return validUntil != null && now.isAfter(validUntil);
        }
    }

    /**
     * The customer confirmed the offer and must prove their identity with a selfie (biometric validation)
     * before the anticipation is requested. {@code attempts} counts the rejected selfies.
     */
    record AwaitingSelfie(String offerId, Money requestedAmount, Money netAmount, Instant validUntil, int attempts)
            implements JourneyStage {

        public AwaitingSelfie(final ReviewingOffer confirmed) {
            this(confirmed.offerId(), confirmed.requestedAmount(), confirmed.netAmount(), confirmed.validUntil(), 0);
        }

        public AwaitingSelfie retry() {
            return new AwaitingSelfie(offerId, requestedAmount, netAmount, validUntil, attempts + 1);
        }

        public boolean isExpired(final Instant now) {
            return validUntil != null && now.isAfter(validUntil);
        }
    }

    /** An agent is in charge; the bot stays silent. */
    record HumanHandoff(String reason) implements JourneyStage {}

    /** Anticipation requested successfully. */
    record Completed(String protocol) implements JourneyStage {}

    /** Ended without anticipation (declined, opted out, not eligible, inactive...). */
    record Closed(String reason) implements JourneyStage {

        /** No customer message within the session idle timeout. */
        public static final String INACTIVITY = "inactivity";
        /** Nobody on our side talked within the handoff timeout: the bot takes the customer back. */
        public static final String HANDOFF_TIMEOUT = "handoff_timeout";
        /** An agent finished the handoff. */
        public static final String HANDOFF_RELEASED = "handoff_released";
    }

    default String value() {
        return switch (this) {
            case Started _ -> STARTED;
            case MainMenu _ -> MAIN_MENU;
            case AwaitingAmount _ -> AWAITING_AMOUNT;
            case ReviewingOffer _ -> REVIEWING_OFFER;
            case AwaitingSelfie _ -> AWAITING_SELFIE;
            case HumanHandoff _ -> HUMAN_HANDOFF;
            case Completed _ -> COMPLETED;
            case Closed _ -> CLOSED;
        };
    }

    default boolean isTerminal() {
        return this instanceof Completed || this instanceof Closed;
    }

    default boolean canAdvanceTo(final JourneyStage next) {
        if (next == null || this.isTerminal()) {
            return false;
        }

        return switch (next) {
            case Closed _, HumanHandoff _ -> true;
            case Started _ -> false;
            case MainMenu _ -> true;
            case AwaitingAmount _ -> !(this instanceof HumanHandoff);
            case ReviewingOffer _ -> this instanceof AwaitingAmount || this instanceof ReviewingOffer
                    || this instanceof AwaitingSelfie || this instanceof MainMenu || this instanceof Started;
            case AwaitingSelfie _ -> this instanceof ReviewingOffer || this instanceof AwaitingSelfie;
            case Completed _ -> this instanceof AwaitingSelfie;
        };
    }

    static Class<? extends JourneyStage> typeOf(final String stage) {
        return switch (stage) {
            case STARTED -> Started.class;
            case MAIN_MENU -> MainMenu.class;
            case AWAITING_AMOUNT -> AwaitingAmount.class;
            case REVIEWING_OFFER -> ReviewingOffer.class;
            case AWAITING_SELFIE -> AwaitingSelfie.class;
            case HUMAN_HANDOFF -> HumanHandoff.class;
            case COMPLETED -> Completed.class;
            case CLOSED -> Closed.class;
            case null, default -> throw DomainException.with("Invalid journey stage: %s".formatted(stage));
        };
    }
}
