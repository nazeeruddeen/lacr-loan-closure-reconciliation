# API Documentation

Base path: `/api/v1/closure-requests`

## Endpoints

| Method | Endpoint | Description |
| --- | --- | --- |
| `POST` | `/` | Create a closure request |
| `GET` | `/{id}` | Get a closure request |
| `POST` | `/{id}/settlement` | Calculate or recalculate settlement |
| `POST` | `/{id}/reconcile` | Reconcile closure values |
| `POST` | `/{id}/status` | Advance workflow status |
| `GET` | `/summary` | Return dashboard totals |
| `GET` | `/search` | Search and filter closure requests with pagination |
| `GET` | `/reports/summary` | Return dashboard/report totals |
| `GET` | `/reports/summary.csv` | Download dashboard/report totals as CSV |
| `GET` | `/reports/events` | Query the lightweight audit/event stream |
| `GET` | `/reports/events.csv` | Download the audit/event stream as CSV |
| `GET` | `/reports/closures.csv` | Download filtered search results as CSV |

## Notes

- `requestId` is used for idempotent create behavior.
- `requestId`, settlement amount, and target status are used to form idempotency keys for repeated workflow calls.
- Status changes are appended to an audit trail and persisted in `loan_closure_events`.
- Idempotent responses are cached in memory and persisted in `loan_closure_idempotency_keys`.
- Search supports loan account number, borrower name, closure status, reconciliation status, and settlement range filters.
- CSV exports are available for summary, events, and filtered closure search results.
