# LACR - Loan Account Closure and Reconciliation

LACR is the loan account closure and reconciliation project. The design is consistency-heavy and backend-focused, with a dashboard shell for demo and interview walkthroughs.

## Scope

- Closure request intake
- Settlement calculation
- Reconciliation workflow
- Idempotent request handling
- Audit trail
- Exception review
- Reporting
- Frontend dashboard shell
- CSV export endpoints for summary, events, and filtered search results

## Planned Stack

- Java 17
- Spring Boot
- Spring Security
- Spring Data JPA
- MySQL
- Redis
- MongoDB
- Kafka
- Flyway

## Current State

The main implementation slice is in place:

- Closure request intake
- Settlement calculation
- Reconciliation flow
- Status history
- Summary reporting
- Audit/event stream
- Idempotent workflow keys
- Persisted audit events and idempotency keys
- CSV exports for summary, events, and filtered search results

## API Docs

- [API_DOCS.md](./API_DOCS.md)

## Ports

- Backend: `8012`
- Frontend: `4500`

## Run Locally

Backend:

```bash
cd backend
mvn clean test
mvn spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm start
```

## Next Build Slice

1. Finalize the domain model
2. Add finer-grained reconciliation exceptions
3. Add report/export endpoints
4. Expand the audit/event store
5. Add distributed idempotency and retry handling
6. Wire the Angular dashboard to the backend APIs
