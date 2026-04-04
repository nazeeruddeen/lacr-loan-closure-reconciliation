CREATE TABLE loan_closure_consumed_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    idempotency_key VARCHAR(180) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    closure_case_id BIGINT NULL,
    loan_account_number VARCHAR(40) NULL,
    event_type VARCHAR(60) NULL,
    aggregate_type VARCHAR(60) NULL,
    topic_name VARCHAR(160) NOT NULL,
    partition_number INT NOT NULL,
    record_offset BIGINT NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    consumed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_lacr_consumed_idempotency UNIQUE (idempotency_key),
    KEY idx_lacr_consumed_request_id (request_id),
    KEY idx_lacr_consumed_topic_offset (topic_name, partition_number, record_offset)
);
