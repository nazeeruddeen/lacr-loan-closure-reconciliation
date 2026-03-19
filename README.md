# LACR - Loan Account Closure and Reconciliation

LACR is the loan account closure and reconciliation project scaffold. The target design is consistency-heavy and backend-focused:

- closure request intake
- settlement calculation
- reconciliation workflow
- idempotent request handling
- audit trail
- exception review
- reporting
- frontend dashboard shell
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

The first implementation slice is in place:

- closure request intake
- settlement calculation
- reconciliation flow
- status history
- summary reporting
- audit/event stream
- idempotent workflow keys
- persisted audit events and idempotency keys
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
