# ADR 0011: Order submission idempotency

- **Status:** Accepted
- **Context:** Clients may retry `POST /api/v1/orders` after losing a response. Duplicate orders and duplicate reservation commands must be prevented while allowing an expired key to be reused.
- **Decision:** Scope `Idempotency-Key` to the authenticated customer subject. Persist a SHA-256 fingerprint of normalized product quantities and the development payment token for 24 hours. Matching requests replay the same order identifier and current accepted representation; a different fingerprint within the active period returns HTTP 409. PostgreSQL transaction-scoped advisory locking serializes a customer/key pair, and order creation, idempotency record, history, and reservation outbox command commit together.
- **Options:** In-memory cache (not shared or durable); database uniqueness alone (does not serialize a missing-row race); permanent keys (unbounded retention and no key reuse).
- **Trade-offs:** PostgreSQL-specific advisory locking is appropriate to the selected database and makes the absent-key race safe. Expired keys can point to a newly created order while the original order and its history remain retained.
