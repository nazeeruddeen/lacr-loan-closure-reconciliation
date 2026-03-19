create table loan_closure_events (
    id bigint not null auto_increment,
    request_id varchar(64) not null,
    closure_case_id bigint not null,
    loan_account_number varchar(40) not null,
    event_type varchar(50) not null,
    from_status varchar(40) null,
    to_status varchar(40) null,
    reconciliation_status varchar(30) null,
    actor varchar(120) null,
    details varchar(1000) null,
    created_at datetime(6) not null,
    primary key (id),
    key idx_loan_closure_events_request_id (request_id),
    key idx_loan_closure_events_loan_account_number (loan_account_number),
    key idx_loan_closure_events_event_type (event_type),
    key idx_loan_closure_events_created_at (created_at)
);

create table loan_closure_idempotency_keys (
    id bigint not null auto_increment,
    idempotency_key varchar(180) not null,
    response_type varchar(120) not null,
    payload_json longtext not null,
    expires_at datetime(6) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    unique key uk_loan_closure_idempotency_key (idempotency_key),
    key idx_loan_closure_idempotency_expires_at (expires_at)
);
