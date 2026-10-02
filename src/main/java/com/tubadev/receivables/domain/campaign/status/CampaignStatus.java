package com.tubadev.receivables.domain.campaign.status;

import com.tubadev.receivables.domain.exceptions.DomainException;

/**
 * State pattern for the campaign lifecycle: DRAFT -> RUNNING -> COMPLETED, with CANCELED reachable
 * from DRAFT and RUNNING. Invalid transitions are rejected; repeating the current one is a no-op.
 */
public sealed interface CampaignStatus permits
        CampaignStatus.Draft,
        CampaignStatus.Running,
        CampaignStatus.Completed,
        CampaignStatus.Canceled {

    String DRAFT = "draft";
    String RUNNING = "running";
    String COMPLETED = "completed";
    String CANCELED = "canceled";

    default CampaignStatus start() { throw invalidTransition(RUNNING); }
    default CampaignStatus complete() { throw invalidTransition(COMPLETED); }
    default CampaignStatus cancel() { throw invalidTransition(CANCELED); }

    default String value() {
        return switch (this) {
            case Draft _ -> DRAFT;
            case Running _ -> RUNNING;
            case Completed _ -> COMPLETED;
            case Canceled _ -> CANCELED;
        };
    }

    default boolean isFinal() {
        return this instanceof Completed || this instanceof Canceled;
    }

    private DomainException invalidTransition(final String target) {
        return DomainException.with("Campaign with status %s can´t transit to %s".formatted(value(), target));
    }

    static CampaignStatus create(final String status) {
        if (status == null) {
            throw DomainException.with("'status' should not be null");
        }

        return switch (status) {
            case DRAFT -> new Draft();
            case RUNNING -> new Running();
            case COMPLETED -> new Completed();
            case CANCELED -> new Canceled();
            default -> throw DomainException.with("Invalid campaign status: %s".formatted(status));
        };
    }

    record Draft() implements CampaignStatus {
        @Override public CampaignStatus start() { return new Running(); }
        @Override public CampaignStatus cancel() { return new Canceled(); }
    }

    record Running() implements CampaignStatus {
        @Override public CampaignStatus start() { return this; }
        @Override public CampaignStatus complete() { return new Completed(); }
        @Override public CampaignStatus cancel() { return new Canceled(); }
    }

    record Completed() implements CampaignStatus {
        @Override public CampaignStatus complete() { return this; }
    }

    record Canceled() implements CampaignStatus {
        @Override public CampaignStatus cancel() { return this; }
    }
}
