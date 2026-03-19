# LACR - Loan Account Closure and Reconciliation

This workspace is reserved for the resume project:

- Loan Account Closure and Reconciliation

Planned scope:

- Closure request intake
- Settlement calculation
- Reconciliation status engine
- Audit trail
- Idempotent request handling
- Reporting and exception review
- Angular dashboard shell

Current slice:

- Intake, settlement, reconciliation, status history, search, summary, and event-report APIs are implemented in the backend skeleton.
- Audit events and idempotency keys now persist to database-backed tables with in-memory fallback for tests.
- CSV export endpoints are implemented for summary, events, and filtered search results.
- The Angular dashboard shell has been started.

Notes:

- This project will be built after the core lending workflow project.
- It should be treated as a backend-heavy system with stronger workflow and consistency guarantees.
- The frontend exists as a presentation shell first; API wiring comes after the backend stabilizes.
