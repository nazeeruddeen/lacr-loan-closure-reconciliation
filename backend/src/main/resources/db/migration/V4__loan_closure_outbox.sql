create table loan_closure_outbox_events (
    id bigint not null auto_increment,
    request_id varchar(64) not null,
    closure_case_id bigint not null,
    loan_account_number varchar(40) not null,
    aggregate_type varchar(60) not null,
    event_type varchar(60) not null,
    payload_json longtext not null,
    publish_status varchar(20) not null,
    attempt_count int not null,
    last_attempt_at datetime(6) null,
    published_at datetime(6) null,
    error_message varchar(500) null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    key idx_loan_closure_outbox_status_created (publish_status, created_at),
    key idx_loan_closure_outbox_request_id (request_id)
);
