# ADR 0004: Saga orchestration

- **Status:** Proposed
- **Decision:** Order Service orchestrates saga progression and compensation and remains the only service that changes order status.
- **Context:** The workflow reserves inventory, authorizes payment, commits the reservation, and notifies customers. Failures require release and possibly refund.
- **Options:** Order-orchestrated saga; choreography in which each service reacts to prior events; a workflow engine.
- **Trade-offs:** Orchestration makes transitions and order history easier to inspect but concentrates workflow logic in Order. Choreography reduces a central coordinator but spreads failure handling across consumers. A workflow engine adds an external runtime and operational cost.
