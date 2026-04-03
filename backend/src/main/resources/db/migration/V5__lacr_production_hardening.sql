ALTER TABLE loan_closure_events
    ADD COLUMN previous_hash VARCHAR(64) NULL AFTER details,
    ADD COLUMN record_hash VARCHAR(64) NULL AFTER previous_hash;

UPDATE loan_closure_events
SET record_hash = SHA2(
    CONCAT_WS('|',
        COALESCE(previous_hash, ''),
        COALESCE(request_id, ''),
        COALESCE(CAST(closure_case_id AS CHAR), ''),
        COALESCE(loan_account_number, ''),
        COALESCE(event_type, ''),
        COALESCE(from_status, ''),
        COALESCE(to_status, ''),
        COALESCE(reconciliation_status, ''),
        COALESCE(actor, ''),
        COALESCE(details, ''),
        COALESCE(created_at, '')
    ),
    256
)
WHERE record_hash IS NULL;

ALTER TABLE loan_closure_events
    MODIFY COLUMN record_hash VARCHAR(64) NOT NULL;

CREATE INDEX idx_loan_closure_events_closure_case_created
    ON loan_closure_events(closure_case_id, created_at);

ALTER TABLE loan_closure_outbox_events
    ADD COLUMN processing_started_at DATETIME(6) NULL AFTER publish_status;

CREATE INDEX idx_loan_closure_outbox_claimable
    ON loan_closure_outbox_events(publish_status, processing_started_at, created_at);
