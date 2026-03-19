# LACR - Loan Account Closure and Reconciliation

A highly resilient, backend-focused distributed system workflow. This project serves as a capstone piece designed to showcase consistency, fault-tolerance, and operational maturity for senior-level engineering interviews.

## 🎯 Core Interview Story: Distributed Systems Maturity
This project provides a "Senior Signal" resume story. Instead of relying on a monolithic database lock, it implements patterns essential to distributed systems—idempotency, optimistic locking, Dead Letter Tables, and structured MDC logging.

### Key Architectural Decisions & Features
*   **Idempotency Engine:** Integrates a `LoanClosureIdempotencyStore` to safely handle duplicate incoming API requests. Prevents duplicate closure calculations even if a client retries a timeout.
*   **Optimistic Locking (`@Version`):** Protects the `loan_closure_cases` table from parallel reconciliation races (e.g., a scheduled job and a manual API trigger both trying to reconcile the same file simultaneously), throwing `OptimisticLockingFailureException` instead of deadlocking.
*   **Dead Letter Table (DLT) Pattern:** Rather than discarding failed events after retries are exhausted, the system persists them fully payload-intact to a `failed_events` table utilizing a decoupled `PROPAGATION_REQUIRES_NEW` transaction constraint. This ensures the failure record always commits, independent of the parent rollback.
*   **Distributed Tracing (MDC):** Employs an `MdcCorrelationIdFilter` to track `X-Correlation-Id` across the entire request lifecycle. The Logback JSON encoder propagates this ID into every single log line, effectively solving the "needle in a haystack" problem of debugging concurrent closure workflows.
*   **Micrometer Metrics:** Exposes PromQL-ready custom metrics including counters for `lacr.reconciliation_exceptions_total` and timers with latency percentiles (p50/p95/p99) for the settlement calculation processes.

## 🛠 Tech Stack
*   **Java 17** & **Spring Boot 3.2**
*   **Spring Data JPA** (MySQL with `@Version` columns)
*   **Flyway** (Database migrations)
*   **Micrometer Prometheus Registry** (Custom Service Metrics)
*   **Logstash Logback Encoder** (Structured MDC JSON Logging)
*   **Swagger/OpenAPI** & **Actuator**

## 🚀 Run Locally

**Backend:**
```bash
cd backend
mvn clean test
mvn spring-boot:run
```

**Ports:**
*   API / Swagger UI: `http://localhost:8012/swagger-ui.html`
*   Prometheus Metrics: `http://localhost:8012/actuator/prometheus`
