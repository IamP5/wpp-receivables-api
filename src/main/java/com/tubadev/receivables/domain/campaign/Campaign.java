package com.tubadev.receivables.domain.campaign;

import com.tubadev.receivables.domain.AggregateRoot;
import com.tubadev.receivables.domain.campaign.CampaignCommand.CancelCampaign;
import com.tubadev.receivables.domain.campaign.CampaignCommand.CompleteCampaign;
import com.tubadev.receivables.domain.campaign.CampaignCommand.StartCampaign;
import com.tubadev.receivables.domain.campaign.status.CampaignStatus;
import com.tubadev.receivables.domain.customer.CustomerId;
import com.tubadev.receivables.domain.utils.InstantUtils;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.SequencedSet;

/**
 * A business-initiated outreach (e.g. "you have boletos eligible for anticipation") sent as an approved
 * WhatsApp template to a set of opted-in customers.
 */
public class Campaign extends AggregateRoot<CampaignId> {

    private static final int MAX_RECIPIENTS = 10_000;

    private int version;
    private String name;
    private CampaignTemplate template;
    private SequencedSet<CustomerId> recipients;
    private CampaignStatus status;
    private DispatchStats stats;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant startedAt;
    private Instant finishedAt;

    private Campaign(
            final CampaignId anId,
            final int version,
            final String aName,
            final CampaignTemplate aTemplate,
            final Collection<CustomerId> recipients,
            final CampaignStatus aStatus,
            final DispatchStats stats,
            final Instant createdAt,
            final Instant updatedAt,
            final Instant startedAt,
            final Instant finishedAt
    ) {
        super(anId);
        this.setVersion(version);
        this.setName(aName);
        this.setTemplate(aTemplate);
        this.setRecipients(recipients);
        this.setStatus(aStatus);
        this.setStats(stats);
        this.setCreatedAt(createdAt);
        this.setUpdatedAt(updatedAt);
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public static Campaign newCampaign(
            final CampaignId anId,
            final String aName,
            final CampaignTemplate aTemplate,
            final Collection<CustomerId> recipients
    ) {
        final var now = InstantUtils.now();
        final var aCampaign = new Campaign(anId, 0, aName, aTemplate, recipients, new CampaignStatus.Draft(),
                DispatchStats.EMPTY, now, now, null, null);
        aCampaign.registerEvent(new CampaignCreated(aCampaign));
        return aCampaign;
    }

    public static Campaign with(
            final CampaignId anId,
            final int version,
            final String aName,
            final CampaignTemplate aTemplate,
            final Collection<CustomerId> recipients,
            final String aStatus,
            final DispatchStats stats,
            final Instant createdAt,
            final Instant updatedAt,
            final Instant startedAt,
            final Instant finishedAt
    ) {
        return new Campaign(anId, version, aName, aTemplate, recipients, CampaignStatus.create(aStatus), stats,
                createdAt, updatedAt, startedAt, finishedAt);
    }

    public void execute(final CampaignCommand... cmds) {
        if (cmds == null || cmds.length == 0) {
            return;
        }

        for (var cmd : cmds) {
            switch (cmd) {
                case StartCampaign _ -> applyStart();
                case CompleteCampaign(var stats) -> applyComplete(stats);
                case CancelCampaign _ -> applyCancel();
            }
        }

        this.setUpdatedAt(InstantUtils.now());
    }

    public boolean isRunning() {
        return this.status instanceof CampaignStatus.Running;
    }

    public int version() {
        return version;
    }

    public String name() {
        return name;
    }

    public CampaignTemplate template() {
        return template;
    }

    public SequencedSet<CustomerId> recipients() {
        return Collections.unmodifiableSequencedSet(recipients);
    }

    public CampaignStatus status() {
        return status;
    }

    public DispatchStats stats() {
        return stats;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    private void applyStart() {
        if (this.isRunning()) {
            return;
        }
        this.setStatus(this.status.start());
        this.startedAt = InstantUtils.now();
        this.registerEvent(new CampaignStarted(this));
    }

    private void applyComplete(final DispatchStats stats) {
        this.setStatus(this.status.complete());
        this.setStats(stats);
        this.finishedAt = InstantUtils.now();
        this.registerEvent(new CampaignCompleted(this));
    }

    private void applyCancel() {
        if (this.status instanceof CampaignStatus.Canceled) {
            return;
        }
        this.setStatus(this.status.cancel());
        this.finishedAt = InstantUtils.now();
        this.registerEvent(new CampaignCanceled(this));
    }

    private void setVersion(final int version) {
        this.version = version;
    }

    private void setName(final String name) {
        this.assertArgumentNotEmpty(name, "'name' should not be empty");
        this.name = this.assertArgumentMaxLength(name, 255, "'name' should not exceed 255 characters");
    }

    private void setTemplate(final CampaignTemplate template) {
        this.template = this.assertArgumentNotNull(template, "'template' should not be null");
    }

    private void setRecipients(final Collection<CustomerId> recipients) {
        this.assertArgumentNotEmpty(recipients, "'recipients' should not be empty");
        this.assertConditionTrue(recipients.size() <= MAX_RECIPIENTS, "'recipients' should not exceed %d".formatted(MAX_RECIPIENTS));
        this.recipients = new LinkedHashSet<>(recipients);
    }

    private void setStatus(final CampaignStatus status) {
        this.status = this.assertArgumentNotNull(status, "'status' should not be null");
    }

    private void setStats(final DispatchStats stats) {
        this.stats = this.assertArgumentNotNull(stats, "'stats' should not be null");
    }

    private void setCreatedAt(final Instant createdAt) {
        this.createdAt = this.assertArgumentNotNull(createdAt, "'createdAt' should not be null");
    }

    private void setUpdatedAt(final Instant updatedAt) {
        this.updatedAt = this.assertArgumentNotNull(updatedAt, "'updatedAt' should not be null");
    }
}
