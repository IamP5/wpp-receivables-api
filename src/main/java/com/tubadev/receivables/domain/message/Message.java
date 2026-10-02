package com.tubadev.receivables.domain.message;

import com.tubadev.receivables.domain.AggregateRoot;
import com.tubadev.receivables.domain.conversation.ConversationId;
import com.tubadev.receivables.domain.message.MessageCommand.MarkAccepted;
import com.tubadev.receivables.domain.message.MessageCommand.MarkDelivered;
import com.tubadev.receivables.domain.message.MessageCommand.MarkFailed;
import com.tubadev.receivables.domain.message.MessageCommand.MarkRead;
import com.tubadev.receivables.domain.message.MessageCommand.MarkSent;
import com.tubadev.receivables.domain.message.status.MessageStatus;
import com.tubadev.receivables.domain.utils.InstantUtils;

import java.time.Instant;

public class Message extends AggregateRoot<MessageId> {

    private int version;
    private ConversationId conversationId;
    private Direction direction;
    private String wamid;
    private MessageContent content;
    private MessageStatus status;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;

    private Message(
            final MessageId anId,
            final int version,
            final ConversationId aConversationId,
            final Direction aDirection,
            final String aWamid,
            final MessageContent aContent,
            final MessageStatus aStatus,
            final String aFailureReason,
            final Instant createdAt,
            final Instant updatedAt
    ) {
        super(anId);
        this.setVersion(version);
        this.setConversationId(aConversationId);
        this.setDirection(aDirection);
        this.setWamid(aWamid);
        this.setContent(aContent);
        this.setStatus(aStatus);
        this.setFailureReason(aFailureReason);
        this.setCreatedAt(createdAt);
        this.setUpdatedAt(updatedAt);
    }

    /**
     * {@code createdAt} is the moment we recorded it (it orders the conversation); the WhatsApp timestamp of the
     * message only drives the customer service window of the {@code Conversation}.
     */
    public static Message newInbound(
            final MessageId anId,
            final ConversationId aConversationId,
            final String aWamid,
            final MessageContent aContent
    ) {
        final var now = InstantUtils.now();
        final var aMessage = new Message(anId, 0, aConversationId, Direction.INBOUND, aWamid, aContent,
                new MessageStatus.Received(), null, now, now);
        aMessage.assertArgumentNotEmpty(aWamid, "Inbound message 'wamid' should not be empty");
        aMessage.registerEvent(new MessageReceived(aMessage));
        return aMessage;
    }

    public static Message newOutbound(final MessageId anId, final ConversationId aConversationId, final MessageContent aContent) {
        final var now = InstantUtils.now();
        return new Message(anId, 0, aConversationId, Direction.OUTBOUND, null, aContent, new MessageStatus.Pending(), null, now, now);
    }

    public static Message with(
            final MessageId anId,
            final int version,
            final ConversationId aConversationId,
            final Direction aDirection,
            final String aWamid,
            final MessageContent aContent,
            final String aStatus,
            final String aFailureReason,
            final Instant createdAt,
            final Instant updatedAt
    ) {
        return new Message(anId, version, aConversationId, aDirection, aWamid, aContent,
                MessageStatus.create(aStatus), aFailureReason, createdAt, updatedAt);
    }

    public void execute(final MessageCommand... cmds) {
        if (cmds == null || cmds.length == 0) {
            return;
        }

        for (var cmd : cmds) {
            final var previous = this.status;
            switch (cmd) {
                case MarkAccepted(var aWamid) -> {
                    this.setWamid(aWamid);
                    this.transitTo(previous.accept());
                }
                case MarkSent _ -> this.transitTo(previous.sent());
                case MarkDelivered _ -> this.transitTo(previous.delivered());
                case MarkRead _ -> this.transitTo(previous.read());
                case MarkFailed(var aReason) -> {
                    this.transitTo(previous.fail());
                    if (this.isFailed() && !previous.equals(this.status)) {
                        this.setFailureReason(aReason);
                        this.registerEvent(new MessageFailed(this));
                    }
                }
            }
        }

        this.setUpdatedAt(InstantUtils.now());
    }

    public boolean isInbound() {
        return Direction.INBOUND == this.direction;
    }

    public boolean isFailed() {
        return this.status instanceof MessageStatus.Failed;
    }

    public int version() {
        return version;
    }

    public ConversationId conversationId() {
        return conversationId;
    }

    public Direction direction() {
        return direction;
    }

    public String wamid() {
        return wamid;
    }

    public MessageContent content() {
        return content;
    }

    public MessageStatus status() {
        return status;
    }

    public String failureReason() {
        return failureReason;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private void transitTo(final MessageStatus next) {
        final var previous = this.status;
        this.setStatus(next);
        if (!previous.equals(next)) {
            this.registerEvent(new MessageStatusChanged(this, previous.value()));
        }
    }

    private void setVersion(final int version) {
        this.version = version;
    }

    private void setConversationId(final ConversationId conversationId) {
        this.conversationId = this.assertArgumentNotNull(conversationId, "'conversationId' should not be null");
    }

    private void setDirection(final Direction direction) {
        this.direction = this.assertArgumentNotNull(direction, "'direction' should not be null");
    }

    private void setWamid(final String wamid) {
        this.wamid = wamid;
    }

    private void setContent(final MessageContent content) {
        this.content = this.assertArgumentNotNull(content, "'content' should not be null");
    }

    private void setStatus(final MessageStatus status) {
        this.status = this.assertArgumentNotNull(status, "'status' should not be null");
    }

    private void setFailureReason(final String failureReason) {
        this.failureReason = failureReason;
    }

    private void setCreatedAt(final Instant createdAt) {
        this.createdAt = this.assertArgumentNotNull(createdAt, "'createdAt' should not be null");
    }

    private void setUpdatedAt(final Instant updatedAt) {
        this.updatedAt = this.assertArgumentNotNull(updatedAt, "'updatedAt' should not be null");
    }
}
