# ADR 0002: REST and messaging

- **Status:** Proposed
- **Decision:** Use REST for customer/operations APIs, reads, and Catalog lookups that need an immediate response; use asynchronous messaging for order workflow commands and results.
- **Context:** Order creation returns `202 Accepted`, while the saga continues across service boundaries. Catalog snapshots are needed at order creation, and operational reads need direct responses.
- **Options:** REST for all service coordination; messaging for all coordination; use each according to whether a synchronous answer is required.
- **Trade-offs:** REST is easy to understand but couples availability and response time. Messaging improves isolation and recoverability but adds eventual consistency, duplicate handling, and queue operations. Catalog lookup availability and timeout policy remain open.
