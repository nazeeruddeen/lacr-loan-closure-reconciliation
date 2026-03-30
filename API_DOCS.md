# API Documentation

Base paths:
- Workflow API: `/api/v1/closure-requests`
- Operator profile API: `/api/v1/operators`

## Authentication
All `/api/**` endpoints require HTTP Basic authentication.

Example local header:
```http
Authorization: Basic base64(closureops:Closure@123)
```

## Operator endpoint
| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/api/v1/operators/me` | Returns the current authenticated operator profile |

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
- Audit events capture operator actor, status transition, reconciliation state, and free-form remarks.
- Validation and business-rule failures return structured error payloads with request path and validation details when applicable.
