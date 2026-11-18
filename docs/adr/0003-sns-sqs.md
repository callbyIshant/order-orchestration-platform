# ADR 0003: SNS/SQS topology

- **Status:** Proposed
- **Decision:** Use service-specific standard SQS command queues and a shared SNS domain-events topic with a separate SQS subscription per consumer; configure a DLQ for each source queue.
- **Context:** Inventory and Payment receive commands; Order consumes workflow results; Notification consumes final order outcomes. Delivery is at least once.
- **Options:** SNS fan-out plus SQS subscriptions; direct service-to-service SQS queues only; FIFO queues where ordered processing is needed.
- **Trade-offs:** SNS/SQS separates publishers and consumers and supports independent retries, but requires routing, policy, and redrive configuration. Standard queues permit duplicates and do not guarantee ordering; FIFO adds ordering and deduplication constraints that are not yet required. Diagrams must clarify how Order publishes final outcomes.
