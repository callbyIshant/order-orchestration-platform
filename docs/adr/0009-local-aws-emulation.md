# ADR 0009: Local AWS emulation

- **Status:** Proposed
- **Decision:** Use LocalStack for local SNS/SQS development and integration tests; keep PostgreSQL and other dependencies in Docker Compose as specified in the architecture document.
- **Context:** Developers need repeatable local integration without provisioning cloud resources. Unit tests can use mocks where they do not need broker behavior.
- **Options:** LocalStack; mocks only; shared AWS development account.
- **Trade-offs:** LocalStack exercises AWS-shaped APIs and routing without cloud resources, but behavior and feature availability can differ from AWS and require Docker. Mocks are fast but do not test broker integration. A shared AWS account is closer to production but adds cost, credentials, and isolation concerns.
