# LACR - Loan Account Closure and Reconciliation

LACR is an operations-focused backend application for loan closure processing. It models the closure lifecycle end to end: intake, settlement calculation, reconciliation, approval, closure, audit traceability, CSV reporting, and idempotent workflow commands.

## Why this project matters
- Shows strong backend workflow design and state-transition control
- Demonstrates Redis-backed idempotency for retry-safe operations
- Tracks every state change in a Mongo-backed audit stream with durable fallback
- Records publishable workflow events through an outbox-style hook for downstream integration
- Surfaces Prometheus metrics and structured correlation logging
- Ships with Docker, Kubernetes, and Jenkins runtime assets
- Includes a live Angular operator console and recovery surface instead of a static shell

## Core workflow
1. Create a closure request
2. Calculate settlement with waivers or adjustments
3. Move the case into reconciliation
4. Reconcile against bank-confirmed amount
5. Approve matched cases or place mismatches on hold / reject
6. Close approved cases
7. Search, audit, and export operational data

## Tech stack
- Java 17
- Spring Boot 3.2
- Spring Data JPA
- Spring Security (HTTP Basic for operator access)
- MySQL + Flyway
- MongoDB-backed audit/event store with durable fallback
- Redis-backed idempotency cache with durable fallback
- Micrometer Prometheus
- Structured Logback JSON logging with MDC correlation IDs
- Angular standalone frontend
- Docker, Kubernetes, Jenkins

## Operator accounts
Usernames are fixed by role, while passwords must be supplied through environment variables or the production secret store:

| Username | Role |
| --- | --- |
| `closureops` | `ROLE_CLOSURE_OPS` |
| `reconlead` | `ROLE_RECON_LEAD` |
| `auditor` | `ROLE_AUDITOR` |
| `opsadmin` | `ROLE_OPS_ADMIN` |

## Local run
### Backend
```bash
cd backend
mvn clean test
mvn spring-boot:run
```

Provide datasource credentials and operator passwords through environment variables before using direct backend startup.

### Full local stack
```bash
docker compose up -d --build
```

Create a local `.env` from `.env.example` before bringing up the stack so operator passwords and database credentials are not committed in the repository.

### Frontend
```bash
cd frontend
npm install
npm start
```

## Default ports
- Backend API: `http://localhost:8012`
- Swagger UI: `http://localhost:8012/swagger-ui.html`
- Health: `http://localhost:8012/actuator/health`
- Prometheus metrics: `http://localhost:8012/actuator/prometheus`
- Frontend: `http://localhost:4500`

## Runtime assets
- Docker Compose: [docker-compose.yml](./docker-compose.yml)
- Backend Docker image: [backend/Dockerfile](./backend/Dockerfile)
- Kubernetes manifests: [k8s](./k8s)
- Jenkins pipeline: [Jenkinsfile](./Jenkinsfile)

## Production deployment posture
- backend pods run as a rolling two-replica deployment in Kubernetes
- application secrets and connection settings are expected to come from an External Secrets store, not committed manifest values
- the in-repo MySQL, Redis, and Mongo manifests are for integration environments only; production should use managed HA backing services

## Interview-ready highlights
- Idempotency is implemented through `LoanClosureIdempotencyStore`, which now uses Redis as the fast-path store while preserving a durable fallback path.
- Audit visibility is preserved through `LoanClosureAuditStore`, which now uses MongoDB as the primary audit/event store while preserving fallback behavior.
- Workflow side effects are coordinated through `LoanClosureWorkflowRecorder`, which keeps audit persistence and publishable event recording aligned.
- Publishability is modeled through `LoanClosureOutboxService`, which stores pending workflow events and retries publication through a scheduled job.
- Stale outbox rows are observable and recoverable through the `/api/v1/ops/outbox/*` operator endpoints and the recovery job.
- Audit events now expose hash-chain metadata so traceability can be verified from the console and CSV exports.
- Actor attribution now comes from authenticated operator identity, so history and audit entries show who performed each action.
- The frontend is a secured operator console, not a mock dashboard fallback.

## Operational references
- [API docs](./API_DOCS.md)
- [Operator runbook](./RUNBOOK.md)
- [Project scope](./PROJECT_SCOPE.md)
