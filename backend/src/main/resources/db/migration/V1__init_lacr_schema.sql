create table loan_closure_cases (
    id bigint not null auto_increment,
    request_id varchar(64) not null,
    loan_account_number varchar(40) not null,
    borrower_name varchar(150) not null,
    outstanding_principal decimal(15,2) not null,
    accrued_interest decimal(15,2) not null,
    penalty_amount decimal(15,2) not null,
    processing_fee decimal(15,2) not null,
    settlement_adjustment decimal(15,2) not null,
    settlement_amount decimal(15,2) not null,
    bank_confirmed_amount decimal(15,2) null,
    settlement_difference decimal(15,2) null,
    closure_status varchar(40) not null,
    reconciliation_status varchar(30) not null,
    closure_reason varchar(200) not null,
    remarks varchar(1000) null,
    created_by varchar(120) null,
    requested_at datetime(6) null,
    calculated_at datetime(6) null,
    reconciled_at datetime(6) null,
    approved_at datetime(6) null,
    closed_at datetime(6) null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    unique key uk_loan_closure_cases_request_id (request_id)
);

create table loan_closure_status_history (
    id bigint not null auto_increment,
    closure_case_id bigint not null,
    from_status varchar(40) null,
    to_status varchar(40) not null,
    action_name varchar(60) not null,
    remarks varchar(1000) null,
    changed_by varchar(120) null,
    changed_at datetime(6) not null,
    primary key (id),
    key idx_loan_closure_status_history_case_id (closure_case_id),
    constraint fk_loan_closure_status_history_case
        foreign key (closure_case_id) references loan_closure_cases (id)
        on delete cascade
);
