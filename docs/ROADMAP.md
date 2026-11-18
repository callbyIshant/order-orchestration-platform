# FulfillX — Implementation Roadmap

Build one working vertical slice at a time. Do not implement all five services in one batch.

## Milestone 0 — Repository and decision baseline

Deliver:
- Maven multi-module repository skeleton.
- Project requirements, architecture, and an ADR index.
- ADRs for service boundaries, SNS/SQS topology, outbox/inbox, saga orchestration, database ownership, and Terraform.
- Version matrix verified from official documentation.

Exit criteria: `./mvnw validate` works; no business logic yet; architecture decisions are reviewed.

## Milestone 1 — Local developer platform

Deliver:
- Docker Compose for PostgreSQL, LocalStack SNS/SQS, and Mailpit; Keycloak may be added when implementing OIDC.
- Health checks and named volumes.
- `.env.example`; no secrets.
- Scripts for startup, teardown, and database initialization.

Exit criteria: a new developer can start local dependencies from documented steps.

## Milestone 2 — Catalog vertical slice

Deliver:
- Catalog Service with product CRUD/read APIs and DTO validation.
- PostgreSQL and Flyway migrations.
- OpenAPI documentation.
- Unit and Testcontainers integration tests.
- Seed data.

Exit criteria: catalog builds and runs independently; database schema is created by Flyway.

## Milestone 3 — Inventory correctness

Deliver:
- Inventory stock API and reservation domain model.
- Atomic multi-line reservation transaction.
- Concurrency protection against overselling.
- Idempotent reservation command handler.
- Inventory outbox and reservation result event.
- Tests for insufficient stock, repeated command, and concurrent reservations.

Exit criteria: a concurrency test proves available stock never falls below zero and reservations are not duplicated.

## Milestone 4 — Order Service and order API

Deliver:
- Order aggregate and item snapshots.
- Idempotency-Key persistence and payload mismatch behaviour.
- `POST /orders` and `GET /orders/{id}`.
- Order database migrations and outbox.
- Catalogue lookup with timeout and defined error mapping.

Exit criteria: API returns 202; retrying the same key returns the same order; no cross-service DB access.

## Milestone 5 — Saga and messaging

Deliver:
- Inventory command/event flow.
- Payment command/event flow with fake tokens only.
- Order saga state transitions.
- Reservation release and payment refund compensation.
- SNS/SQS subscriptions, retry configuration, and DLQs.
- Inbox/processed-message deduplication.

Exit criteria: happy path, insufficient stock, payment decline, duplicate delivery, and compensation tests pass.

## Milestone 6 — Notification and operations

Implementation status: delivered; service integration tests require Docker/Testcontainers to execute.

Deliver:
- Notification consumer and delivery records.
- Mailpit/log sink integration.
- Correlation IDs and structured logs.
- Health, readiness, and Micrometer metrics.
- Basic dashboards/operational runbook.

Exit criteria: a confirmed order results in one logical notification even after duplicate event delivery.

## Milestone 7 — Security and hardening

Implementation status: delivered; service integration tests require Docker/Testcontainers to execute.

Deliver:
- OIDC JWT validation and role-based authorization.
- Customer ownership check on order reads.
- Request size limits, validation, safe error responses.
- Timeouts, bounded retries, DLQ redrive documentation.
- Dependency and container scanning.

Exit criteria: security tests cover unauthenticated, unauthorized, and cross-customer access.

## Milestone 8 — AWS infrastructure

Deliver:
- Terraform VPC, ALB, ECS, ECR, RDS, SNS/SQS, IAM, Secrets Manager, and CloudWatch.
- Separate demo/dev environment variables.
- Deployment circuit breaker and health checks.
- Cost estimate inputs and cleanup runbook.

Exit criteria: `terraform fmt`, `validate`, and `plan` pass; plan is reviewed before any apply.

## Milestone 9 — CI/CD and release

Deliver:
- GitHub Actions PR test pipeline.
- OIDC role assumption and ECR push.
- ECS deploy workflow and smoke tests.
- Version/image traceability and rollback notes.

Exit criteria: a commit can be tested, built, published and deployed without long-lived AWS access keys.

## Milestone 10 — Portfolio polish

Deliver:
- Strong README and architecture diagrams.
- OpenAPI/AsyncAPI and Postman collection.
- Demo script showing successful and failed order workflows.
- k6 smoke/load-test script with stated environment and results.
- Screenshots of AWS resources, dashboards, and API responses (no secrets).
- Short demo video or GIF, ADRs, setup guide, troubleshooting guide, cost/cleanup guide.

Exit criteria: another developer can run the system locally, follow one order through the saga, understand trade-offs, and reproduce the deployment.

## Suggested interview demo (5–8 minutes)

1. Show products and stock for a product with 5 units.
2. Submit an order for 4 units using `tok_success`; show `202 Accepted` then `CONFIRMED`.
3. Submit two concurrent orders that collectively request more than the remaining stock; show only the valid reservation succeeds.
4. Submit an order with `tok_decline`; show reservation release and `CANCELLED`.
5. Repeat an order request with the same Idempotency-Key; show no duplicate order.
6. Show logs/traces/metrics using the correlation ID and show the queue/DLQ operational view.
7. Explain the outbox, inbox, saga, and service database ownership decisions.
