# ADR 0005: Outbox and inbox

- **Status:** Proposed
- **Decision:** Persist outgoing messages in an outbox in the same local transaction as the business update; record consumed message IDs in an inbox/processed-message table in the same transaction as each consumer effect.
- **Context:** Database commits and broker publication cannot share an atomic transaction. Consumers may receive duplicates after crashes or retries.
- **Options:** Transactional outbox/inbox; publish directly after database commit; distributed transactions across databases and messaging.
- **Trade-offs:** Outbox/inbox makes lost publication and duplicate delivery recoverable without claiming exactly-once delivery. It adds publisher polling/locking, retained records, and cleanup work. Direct publication risks losing messages; distributed transactions increase coupling and operational complexity.
