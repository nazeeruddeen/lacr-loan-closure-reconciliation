-- Add optimistic locking version field to loan_closure_cases
-- Default to 0 for existing rows
ALTER TABLE loan_closure_cases
ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Create failed_events table (Application-level Dead Letter Table)
CREATE TABLE failed_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    loan_account_number VARCHAR(40),
    event_payload TEXT,
    failure_reason VARCHAR(500),
    attempt_count INT NOT NULL,
    failed_stage VARCHAR(60),
    created_by VARCHAR(120),
    failed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Index for querying failed events by loan account or request ID
CREATE INDEX idx_failed_events_account ON failed_events(loan_account_number);
CREATE INDEX idx_failed_events_request ON failed_events(request_id);
