# LACR Operator Runbook

This runbook matches the current production hardening in the codebase:
- durable idempotency for closure commands
- hash-chained audit events
- broker-aware outbox publish and recovery flow
- live operator console with recovery controls
- correlation-aware workflow logging and metrics
- ExternalSecret-backed runtime configuration and secret-managed operator passwords

## Normal operating posture
- Use `closureops` or `reconlead` for workflow operations.
- Use `auditor` for read-only audit inspection and exports.
- Use `opsadmin` for system-level recovery actions.
- Keep the dashboard open on the Operations tab during batch closure windows.

## Key surfaces
- Workflow API: `/api/v1/closure-requests`
- Audit/event stream: `/api/v1/closure-requests/reports/events`
- Outbox health: `/api/v1/ops/outbox/health`
- Outbox recovery: `/api/v1/ops/outbox/recover`
- Failed events: `/api/v1/ops/failed-events`

## What to watch
- Closure request volume
- Duplicate closure hits
- Settlement latency
- Reconciliation mismatches
- Outbox pending count
- Outbox processing count
- Outbox stale-processing count
- Failed event count

## Runtime parity
- Kubernetes uses Actuator-backed health probes on `/actuator/health`, `/actuator/health/readiness`, and `/actuator/health/liveness`.
- MySQL, Redis, and MongoDB all have explicit readiness/liveness checks and resource envelopes in the cluster manifests.
- Treat the `docker-compose.yml` stack as local integration parity, not the source of truth for production readiness.
- Production expects managed HA MySQL, Redis, and MongoDB endpoints supplied through the cluster secret store.
- Production also expects a managed Kafka-compatible broker endpoint supplied through platform networking and configuration; the repository does not provision Kafka in-cluster.
- When Kafka publishing is enabled, the in-repo consumer persists `idempotencyKey` markers before acknowledging the event and routes exhausted records into the DLQ plus application dead-letter table.

## Kafka consumer handling
1. Enable Kafka publishing and consumer startup together when running the full event-driven path.
2. Use the `loan_closure_consumed_events` table as the first source of truth for whether an event was already processed.
3. If consumer retries are exhausted, inspect the broker DLQ topic and the `failed_events` table together.
4. Do not replay a DLQ record until the `idempotencyKey` marker and the failure reason both make sense.

## Stale outbox recovery
Use this when the outbox has processing rows older than the reclaim threshold.

1. Open `GET /api/v1/ops/outbox/health`.
2. Confirm `staleProcessingCount > 0`.
3. Call `POST /api/v1/ops/outbox/recover`.
4. Recheck `GET /api/v1/ops/outbox/health`.
5. Verify failed events and the audit stream for the affected `requestId` values.

## Duplicate closure investigation
1. Search the closure by `requestId` in the UI or the audit event stream.
2. Compare the current response with the recorded audit event sequence.
3. Inspect the hash-chain fields `previousHash` and `recordHash` in the event stream.
4. Confirm whether the duplicate was blocked by persistent idempotency or surfaced as a reconciliation anomaly.
5. If the duplicate is real, preserve the evidence trail before any remediation action.

## Audit integrity review
1. Export or query the affected audit events.
2. Verify the record order and hash chain continuity.
3. Check the operator actor and timestamps for each state change.
4. Treat hash mismatch as a tampering or data-integrity incident until proven otherwise.

## Incident checklist
- Do not rewrite historical audit records.
- Do not delete evidence before the review is complete.
- Preserve `requestId`, correlation IDs, and export output.
- Use the operator console to make recovery visible to the team.
- Escalate compliance or finance when duplicate refunds or reconciliation gaps have financial impact.

## Local verification
- Backend: `mvn clean test`
- Frontend: `npm run build`
- Full stack: `docker compose up -d --build`
