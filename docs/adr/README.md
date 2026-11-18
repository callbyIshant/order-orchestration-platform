# Architecture Decision Records

Records are proposals unless marked otherwise. Review and update a record when its decision is accepted or implementation evidence changes it.

| ADR | Topic | Proposed direction | Status |
|---|---|---|---|
| [0001](0001-service-boundaries.md) | Service boundaries | Keep the five documented ownership boundaries | Proposed |
| [0002](0002-rest-and-messaging.md) | REST and messaging | REST for immediate request/response; messaging for workflow steps | Proposed |
| [0003](0003-sns-sqs.md) | SNS/SQS topology | Standard SQS command queues and SNS domain-event fan-out | Proposed |
| [0004](0004-saga-orchestration.md) | Saga orchestration | Order Service orchestrates transitions and compensation | Proposed |
| [0005](0005-outbox-inbox.md) | Outbox/inbox | Transactional outbox and consumer inbox for at-least-once delivery | Proposed |
| [0006](0006-database-ownership.md) | Database ownership | Separate service databases/users; shared RDS instance permitted for demo | Proposed |
| [0007](0007-java-spring-versions.md) | Java/Spring versions | Java 21, Spring Boot 3.5.16; centrally managed dependencies | Proposed |
| [0008](0008-terraform.md) | Terraform | Terraform 1.17.1 baseline; reviewed plan before apply/destroy | Proposed |
| [0009](0009-local-aws-emulation.md) | Local AWS emulation | LocalStack for local SNS/SQS development and integration tests | Proposed |
| [0010](0010-inventory-concurrency.md) | Inventory concurrency | Pessimistic row locks in sorted product UUID order; reserve all lines in one transaction | Accepted for Milestone 3 |
| [0011](0011-order-idempotency.md) | Order idempotency | Customer-scoped key, 24-hour replay window, fingerprint mismatch returns 409 | Accepted for Milestone 4 |
| [0012](0012-order-identity.md) | Order identity | OIDC JWT resource server with local Keycloak realm | Accepted for Milestone 4 |
| [0013](0013-security-observability.md) | Security and observability | JWT at each service boundary; Micrometer, ECS logs, OTLP traces | Accepted for Milestones 6–7 |

## Open follow-ups

- Align architecture diagrams with Order event publication through the shared SNS topic.
- Define the timeout/reconciliation operator workflow for failed reservation release or payment refund.
- Decide whether the demo uses separate RDS databases/users on one instance or separate instances.

## Verification references

- [Java SE support roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html)
- [Spring Boot 3.5 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [AWS SDK for Java 2.x setup](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/setup-java-buildtool.html)
- [AWS SDK for Java v2 releases](https://github.com/aws/aws-sdk-java-v2/releases)
- [Testcontainers for Java](https://java.testcontainers.org/)
- [Terraform install and versions](https://developer.hashicorp.com/terraform/install)
- [Maven Wrapper](https://maven.apache.org/tools/wrapper/)
