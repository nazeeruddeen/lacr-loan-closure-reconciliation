# Project Scope

## Product identity
LACR is an internal operations application for loan-account closure processing. It is designed to reflect backend maturity more than presentation-heavy UI polish.

## Functional scope
- Closure request intake
- Settlement calculation with adjustments
- Reconciliation against bank-confirmed amount
- Controlled status transitions
- Audit event search
- Summary and CSV reporting
- Authenticated operator console

## Engineering scope
- Transactional workflow service
- Redis-backed idempotent write commands with durable fallback
- Mongo-backed audit/event storage with fallback persistence
- Outbox-style workflow event recording with scheduled publish retries
- Operator-visible outbox health and stale-row recovery
- Audit hash-chain traceability in the event stream
- Clearer service boundaries between workflow execution, audit recording, idempotency, and publishability
- Operator attribution in audit and status history
- Structured error contracts
- Docker, Kubernetes, and Jenkins assets
- Prometheus metrics and correlation-aware logging
- A live operator console with recovery controls, not a static dashboard mock
