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
- External access is expected through the ingress manifest at `k8s/08-ingress.yaml`, with `/` routed to the frontend service and `/api` routed to the backend service.
- Replace the default internal placeholder host `lacr.platform.internal` and TLS secret `lacr-tls` with company-owned DNS and certificate values before live deployment.
- Keep Actuator endpoints internal to the cluster unless the platform team explicitly exposes them through a protected operations ingress.
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

## Playwright golden path
- Required env:
  - `LACR_E2E_PASSWORD`
  - optional `LACR_E2E_USERNAME` defaults to `closureops`
  - optional `LACR_E2E_BASE_URL` defaults to `http://127.0.0.1:4500`
  - optional `LACR_E2E_API_BASE_URL` defaults to `http://127.0.0.1:8012`
- Start the local stack first with `docker compose up -d --build`.
- Then run `.\node_modules\.bin\playwright.cmd test` from `frontend`.
- The golden path covers login, settlement calculation, reconciliation start, and matched reconciliation.

## Local observability
- Start the monitoring stack from `observability` with `docker compose up -d`.
- Prometheus: [http://localhost:9093](http://localhost:9093)
- Grafana: [http://localhost:3003](http://localhost:3003)
- Default Grafana credentials:
  - username `admin`
  - password `admin`
- Local Docker scrapes use [prometheus.local.yml](C:\Users\nazee\Desktop\loan_management\lacr-loan-closure-reconciliation\observability\prometheus.local.yml).
- Kubernetes-friendly scrape targets live in [prometheus.k8s.yml](C:\Users\nazee\Desktop\loan_management\lacr-loan-closure-reconciliation\observability\prometheus.k8s.yml).
- Domain alert rules live in [alert-rules.yml](C:\Users\nazee\Desktop\loan_management\lacr-loan-closure-reconciliation\observability\alert-rules.yml).

## Minikube smoke deployment
- Build unique images such as `lacr-loan-closure-reconciliation:smoke-1` and
  `lacr-loan-closure-reconciliation-frontend:smoke-1`.
- Load those images with `minikube image load`.
- Create the smoke secret before backend rollout with MySQL, Redis, Mongo, and
  operator-password values.
- For local smoke validation, override config to:
  - `LACR_OUTBOX_PUBLISHER_MODE=log`
  - `LACR_OUTBOX_KAFKA_CONSUMER_ENABLED=false`
- Set deployment images explicitly with `kubectl set image`.
- Scale the backend to one replica for smoke validation if the cluster does not
  need a two-replica rollout.
- Verify ingress from the ingress controller pod and expect:
  - `308 Permanent Redirect` on HTTP
  - `200 OK` plus frontend HTML on HTTPS
