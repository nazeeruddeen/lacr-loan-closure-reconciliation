# LACR Production Alignment

Product role
- resilience, reconciliation, idempotency, and operations-maturity system in the
  workspace

Production hardening now owned by this repo
- actuator health endpoints are probe-safe
- frontend nginx runtime configuration is production-safe
- cache support is enabled for summary behavior
- idempotency, audit, and outbox paths are part of the production story
- ingress manifest is in place for `/` and `/api`
- MySQL probe timing and backend startup timing are hardened for Kubernetes
- backend, frontend, MySQL, Redis, Mongo, and ingress smoke validation were
  completed successfully in Minikube

Current repo-owned priorities
1. Keep the operational recovery story, auditability, and idempotency paths
   stable.
2. Tighten the Kafka local/dev/prod profile story so production cannot silently
   degrade into log-only publishing.
3. Preserve the backend-first operations posture instead of drifting into
   presentation-only polish.

Smoke validation status
- backend rollout: passed
- frontend rollout: passed
- mysql rollout: passed
- redis rollout: passed
- mongo rollout: passed
- ingress verification: passed

Recommended operator walkthrough
1. Show a closure request lifecycle and operator-facing recovery controls.
2. Explain durable idempotency and audit-chain integrity.
3. Show outbox health and recovery behavior.
4. Explain the production broker posture and why the smoke run disables Kafka
   consumer startup locally.
