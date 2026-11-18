# ADR 0010: Inventory concurrency and atomic reservation

- **Status:** Accepted for Milestone 3
- **Decision:** Inventory takes a pessimistic write lock on each distinct stock row, one at a time in ascending product UUID order. It checks all requested quantities before changing any row, then adjusts every line and persists the reservation and result outbox event in one local transaction.
- **Context:** Orders reserve multiple products atomically, and concurrent orders must not oversell. Inventory consumes at-least-once SQS commands and cannot rely on message ordering.
- **Options:** Pessimistic row locks; conditional atomic updates with `available >= requested`; read-then-decrement without protection.
- **Trade-offs:** Row locks serialize competing reservations for the same products and make multi-line all-or-nothing handling straightforward. Sorted acquisition reduces deadlocks but can reduce throughput on hot products. Conditional updates can offer higher concurrency but need careful all-lines rollback and partial-update handling. Unprotected read-then-decrement is unsafe and rejected.
- **Idempotency:** A unique order ID identifies one reservation; a canonical line hash rejects changed payloads for the same order. A unique processed-message ID makes redelivery of a command a no-op. Reservation and outbox writes share the stock transaction.
- **Evidence:** `InventoryReservationIntegrationTest.competingOrdersCannotReserveMoreThanTheLastUnits` synchronizes two PostgreSQL-backed transactions that each request all remaining units and asserts exactly one reservation succeeds, the other is rejected, and no quantity is oversold.
