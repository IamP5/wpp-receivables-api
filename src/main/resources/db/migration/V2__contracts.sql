-- Assignment contracts issued when an anticipation is requested, with the rendered PDF.

CREATE TABLE contracts (
    id                   VARCHAR(64)   NOT NULL PRIMARY KEY,
    version              INT           NOT NULL DEFAULT 0,
    protocol             VARCHAR(64)   NOT NULL UNIQUE,
    customer_id          VARCHAR(64)   NOT NULL,
    conversation_id      VARCHAR(64)   NOT NULL REFERENCES conversations (id),
    assignor             TEXT          NOT NULL,
    offer                TEXT          NOT NULL,
    expected_credit_date DATE          NOT NULL,
    signature            TEXT          NOT NULL,
    authentication_code  VARCHAR(64)   NOT NULL,
    document_filename    VARCHAR(255),
    document_sha256      VARCHAR(64),
    document             BYTEA,
    issued_at            TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at           TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_contracts_customer ON contracts (customer_id);
CREATE INDEX idx_contracts_conversation ON contracts (conversation_id);
