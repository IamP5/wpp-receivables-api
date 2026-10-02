-- When we (bot or agent) last talked in the session: a human handoff expires after a while without it.

ALTER TABLE conversations ADD COLUMN last_outbound_at TIMESTAMP(6) WITH TIME ZONE;

UPDATE conversations c
SET last_outbound_at = (
    SELECT MAX(m.created_at) FROM messages m
    WHERE m.conversation_id = c.id AND m.direction = 'OUTBOUND' AND m.wamid IS NOT NULL
);
