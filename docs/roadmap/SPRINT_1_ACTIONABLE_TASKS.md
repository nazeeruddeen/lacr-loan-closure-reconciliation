# Sprint 1 Actionable Tasks

## Objective

Align LACR to the flagship production baseline while preserving visibility into its Kafka and reconciliation-specific needs.

## Sprint 1 Priorities

### Config and Environment Alignment

- compare all LACR config keys with Business baseline
- classify variables into config, secret, runtime integration, and local-only buckets
- define staging and prod expectations

### Kubernetes Alignment

- add kustomize base and overlays
- make ingress and allowed origins environment-driven
- validate secret sourcing and Kafka bootstrap configuration ownership

### CI/CD Alignment

- compare Jenkins stages with Business baseline
- add deployment guardrail checks
- define promotion expectations for Kafka-dependent environments

### Security Alignment

- identify local-convenience auth and runtime shortcuts
- align secure cookie and origin policies
- document any break-glass exceptions explicitly

### Architecture Alignment

- document Kafka-specific production concerns
- identify reconciliation-critical failure paths
- ensure LACR production baseline still follows stable core principles

## Expected Outcome

LACR should end Sprint 1 with the same production operating discipline as Business, with Kafka and reconciliation concerns called out instead of being hidden inside ad hoc runtime assumptions.
