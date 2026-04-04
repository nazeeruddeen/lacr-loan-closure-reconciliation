# API Documentation

Base paths:
- Workflow API: `/api/v1/closure-requests`
- Operator profile API: `/api/v1/operators`

## Authentication
All `/api/**` endpoints require HTTP Basic authentication.

Example local header:
```http
Authorization: Basic base64(<operator-username>:<operator-password>)
```

## Operator endpoint
| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/api/v1/operators/me` | Returns the current authenticated operator profile |

## Operations endpoints
| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/api/v1/ops/outbox/health` | Returns pending, processing, failed, stale, and published outbox counts |
| `POST` | `/api/v1/ops/outbox/recover` | Reclaims stale `PROCESSING` outbox rows and republishes them |
| `GET` | `/api/v1/ops/failed-events` | Returns failed workflow events for operator review |

### Operations response shape
- Outbox health includes pending, processing, published, failed, and stale counts, plus oldest/newest timestamps and the reclaim window.
- Outbox recovery returns recovered and republished counts so operators can confirm the retry action completed.
- Failed events include request id, loan account number, failure reason, attempt count, failed stage, actor, and timestamp.

## Workflow endpoints
| Method | Endpoint | Description |
| --- | --- | --- |
| `POST` | `/` | Create a closure request |
| `GET` | `/{id}` | Fetch a closure request by id |
| `POST` | `/{id}/settlement` | Calculate or recalculate settlement |
| `POST` | `/{id}/reconciliation` | Move a closure into reconciliation |
| `POST` | `/{id}/reconcile` | Reconcile against bank-confirmed amount |
| `POST` | `/{id}/status` | Advance the workflow status |
| `GET` | `/summary` | Return queue and amount summary metrics |
| `GET` | `/search` | Search closure cases with pagination |

## Reporting endpoints
| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/reports/summary` | Return summary metrics as JSON |
| `GET` | `/reports/summary.csv` | Export summary metrics as CSV |
| `GET` | `/reports/events` | Query the audit/event stream |
| `GET` | `/reports/events.csv` | Export filtered audit events as CSV |
| `GET` | `/reports/closures.csv` | Export filtered closure queue results as CSV |

## Notes
- `requestId` is the create-operation idempotency anchor.
- Settlement, reconciliation start, reconciliation completion, and status changes each use deterministic idempotency keys.
- Redis is the primary idempotency fast path, with the persistent store retained as a durable fallback.
- MongoDB is the primary audit/event store for event search and CSV export, with fallback persistence retained for resilience.
- Workflow mutations also record outbox-style publishable events for downstream integration and replay-friendly operations.
- The outbox publisher supports `log` and `kafka` delivery modes through runtime configuration.
- Kafka deliveries carry `requestId`, `closureCaseId`, `loanAccountNumber`, `eventType`, `aggregateType`, and `idempotencyKey` headers for downstream consumers.
- The repository includes a Kafka consumer contract that persists `idempotencyKey` values in `loan_closure_consumed_events` before acknowledging business-side consumption.
- Consumer failures are retried through the listener error handler and exhausted records are routed to the configured DLQ topic, then persisted through the application dead-letter table for operator review.
- Stale outbox recovery is an operator action, not a hidden background assumption.
- Audit events include hash-chain metadata (`previousHash`, `recordHash`) so tampering can be detected during review.
- Audit events capture operator actor, status transition, reconciliation state, and free-form remarks.
- Validation and business-rule failures return structured error payloads with request path and validation details when applicable.
