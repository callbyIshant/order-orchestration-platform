# FulfillX — Project Context and Product Specification

## 1. Product vision

FulfillX is a cloud-native order-management and fulfilment backend for a fictional online retailer. It demonstrates how independently deployable services coordinate a business workflow without a shared database or a distributed ACID transaction.

The portfolio objective is to demonstrate sound Java backend engineering, not to imitate a massive enterprise platform. The first release should be small enough to complete, but deep enough to demonstrate distributed-systems fundamentals.

## 2. Users and primary use cases

### Actors

- Customer: browses products, places an order, and checks order status.
- Operations user: maintains catalogue data and stock levels.
- Platform operator: inspects service health, queue failures, and order workflow state.

### Core use cases

1. Browse active products and prices.
2. Create an order containing one or more product IDs and quantities.
3. Submit an idempotency key so retrying a client request does not create a second order.
4. Validate product status and snapshot product name/price at order creation.
5. Reserve all requested inventory atomically within the Inventory Service database.
6. Simulate payment authorization with deterministic success or decline test tokens.
7. Confirm the inventory reservation after payment authorization.
8. Confirm the order and publish a customer notification.
9. Compensate when a step fails: release stock and, where required, refund a prior authorization.
10. Query order status and inspect the order's workflow history.

## 3. Service ownership and boundaries

### Catalog Service

Owns product information and pricing. It exposes read APIs and restricted operations for product maintenance. It does not own stock-on-hand or order state.

Suggested endpoints:
- `GET /api/v1/products`
- `GET /api/v1/products/{productId}`
- `POST /api/v1/products` (operations role)
- `PATCH /api/v1/products/{productId}` (operations role)

Entities: `Product`, `Category`.

### Order Service

Owns customer orders, order-item price/name snapshots, idempotency records, and saga state. It is the saga orchestrator. It is the only service allowed to decide the order's business status.

Suggested endpoints:
- `POST /api/v1/orders` — requires `Idempotency-Key`; returns `202 Accepted` and an order ID.
- `GET /api/v1/orders/{orderId}`
- `GET /api/v1/orders/{orderId}/history`

Entities: `Order`, `OrderItem`, `SagaState` or workflow fields on `Order`, `IdempotencyRecord`, `OutboxMessage`, `ProcessedMessage`.

### Inventory Service

Owns stock quantities and reservations. The reservation of all items in a single order must either succeed as one local transaction or fail without a partial reservation. Concurrency control must prevent overselling.

Suggested endpoints for operations:
- `GET /api/v1/inventory/{productId}`
- `PUT /api/v1/inventory/{productId}` (operations role)

Entities: `InventoryItem`, `Reservation`, `ReservationLine`, `OutboxMessage`, `ProcessedMessage`.

Use either pessimistic row locking with deterministic product ordering or an atomic conditional update such as `UPDATE ... WHERE available_quantity >= requested_quantity`. State the selected strategy in an ADR and prove it with a concurrency test. Do not read a quantity and later decrement it without concurrency protection.

### Payment Service

Owns simulated payment authorizations and refunds. It must never connect to a real payment provider in Release 1 and must never accept or store PAN/CVV data. Accept only a fake payment token such as `tok_success` or `tok_decline`.

Entities: `Payment`, `PaymentTransaction`, `OutboxMessage`, `ProcessedMessage`.

### Notification Service

Consumes final order events and creates notification records. In local development, use Mailpit or a log/email sink. An actual email provider integration is a later enhancement.

Entities: `Notification`, `DeliveryAttempt`, `ProcessedMessage` (and an outbox only if the service emits its own events).

## 4. Order status model

Initial states:
- `PENDING`
- `INVENTORY_RESERVED`
- `PAYMENT_AUTHORIZED`
- `CONFIRMED`
- `REJECTED`
- `CANCELLED`
- `COMPENSATION_PENDING` (operationally useful when a compensating action needs retry)

Only the Order Service changes the order status. Consumers send facts/results to the Order Service; they do not update its database.

## 5. Main saga: successful order

1. Client calls `POST /orders` with items and an idempotency key.
2. Order Service validates the request, obtains catalogue snapshots, and in one local transaction stores the pending order plus a `ReserveInventory` command in its outbox.
3. Outbox publisher sends the command to the Inventory Service SQS queue.
4. Inventory Service consumes the command idempotently, reserves all lines in one local transaction, and writes an `InventoryReserved` result to its outbox.
5. Inventory Service publishes the result to the domain-event SNS topic; an SQS subscription delivers it to the Order Service.
6. Order Service consumes `InventoryReserved`, advances saga state, and writes `AuthorizePayment` to its outbox.
7. Payment Service consumes the command idempotently, simulates authorization, and publishes `PaymentAuthorized` or `PaymentDeclined`.
8. On `PaymentAuthorized`, Order Service writes `ConfirmReservation` to its outbox and advances its workflow state.
9. Inventory Service marks the reservation committed and publishes `InventoryCommitted`.
10. Order Service consumes `InventoryCommitted`, changes the order to `CONFIRMED`, and publishes `OrderConfirmed`.
11. Notification Service consumes `OrderConfirmed` and records/sends the confirmation.

The public API is asynchronous: it returns `202 Accepted`; clients poll the order resource for its final state.

## 6. Failure and compensation scenarios

- Insufficient stock: Inventory publishes `InventoryRejected`; Order becomes `REJECTED`; no payment is attempted.
- Payment declined: Order Service requests `ReleaseReservation`; after the release result (or recorded successful dispatch plus reconciliation), Order becomes `CANCELLED`.
- Inventory commit fails after payment authorization: Order Service requests a payment refund and reservation release; the order enters `COMPENSATION_PENDING` until compensation is confirmed.
- Duplicate command/event: consumer inbox/deduplication prevents a second business effect.
- Consumer crash after database commit but before SQS delete: redelivery is safe because processing is idempotent.
- SNS/SQS outage: outbox rows remain pending and retry later; no database state change is lost.
- Poison message: bounded retries then DLQ; document inspection and redrive procedure.
- Saga timeout: later milestone adds a scheduled reconciliation process for workflows stuck beyond a configured deadline.

Do not claim exactly-once message delivery. Design for at-least-once delivery and idempotent effects.

## 7. API and message conventions

### REST conventions

- Base path: `/api/v1`.
- Use UUIDs for externally visible identifiers.
- Validate request DTOs at the API boundary.
- Return `ProblemDetail`-style structured errors with a stable error code and correlation ID.
- Never expose JPA entities directly.
- Use pagination for collection endpoints.
- Use optimistic concurrency or explicit update semantics where concurrent modifications matter.

### Idempotent order request

Example request (illustrative):

```http
POST /api/v1/orders
Idempotency-Key: order-demo-001
Content-Type: application/json

{
  "items": [
    { "productId": "<uuid>", "quantity": 2 }
  ],
  "paymentToken": "tok_success"
}
```

Do not include real card details. The same user + idempotency key must return the original order/result rather than create another order. Define key expiry and payload-mismatch behaviour explicitly.

### Message envelope

Every command/event should carry:

```json
{
  "messageId": "<uuid>",
  "messageType": "InventoryReserved",
  "schemaVersion": 1,
  "occurredAt": "2026-01-01T12:00:00Z",
  "correlationId": "<uuid>",
  "causationId": "<uuid>",
  "aggregateType": "Order",
  "aggregateId": "<uuid>",
  "payload": {}
}
```

The date is illustrative. Store event schemas under `contracts/schemas/`; document routing and channels under `contracts/asyncapi/`. Keep event names and payloads backward-compatible. Do not put secrets, full JWTs, or sensitive payment data in events.

## 8. Persistence and consistency rules

- Each service owns its database/schema, credentials, tables, and migrations.
- For a low-cost demo only, separate databases/users on one RDS instance are acceptable; document the shared-instance compromise.
- Order Service stores a price/name snapshot so future catalogue price changes do not rewrite old orders.
- Inventory reservation and stock adjustment happen in one Inventory Service transaction.
- Use Flyway migrations. Hibernate may validate mappings but must not create/update production schema.
- Use an outbox table in services that need to atomically persist state and publish a message.
- Use an inbox/processed-message table with a unique message ID for message consumers.
- Do not share cross-service JPA models or perform cross-database joins.

## 9. Security requirements

- OIDC/OAuth2 JWT validation at service/API boundaries.
- Suggested roles: `CUSTOMER`, `OPERATIONS`, `ADMIN`.
- Customers can read their own orders; operations users can manage products and stock; administrators can inspect operational endpoints.
- Use AWS Cognito as the hosted AWS identity provider. Local development may use Keycloak with the same OIDC contract, or a clearly isolated local test security profile.
- ECS task roles grant only the AWS actions each service needs.
- Store credentials and provider secrets in AWS Secrets Manager. Do not put secrets in Terraform variables committed to Git.
- Require HTTPS at ingress, restrict security-group paths, keep RDS private, and enable encryption at rest.
- Do not log access tokens, passwords, payment tokens, or sensitive personal data.

## 10. Observability requirements

- Structured JSON logs with timestamp, level, service, trace ID, span ID, correlation ID, and message.
- Micrometer metrics: request count/latency/error rate, DB pool, consumer success/failure, queue age/depth, DLQ count, saga duration, order outcome counts.
- Readiness and liveness health checks; readiness must verify only dependencies needed to accept work.
- Distributed tracing using OpenTelemetry/Micrometer Tracing with OTLP; local Jaeger and AWS ADOT/X-Ray exporters.
- CloudWatch log groups, dashboard, and alarms for service errors, queue depth/age, DLQ messages, and unhealthy targets.

## 11. Quality attributes and acceptance criteria

- Services can be built and started independently.
- Local environment can start required dependencies with documented commands.
- A customer can place an order and observe its eventual final status.
- Insufficient stock prevents an order from being confirmed.
- Payment decline triggers reservation compensation.
- Retrying the same idempotency key does not create a duplicate order.
- Delivering the same message more than once does not double-reserve stock or double-authorize payment.
- Concurrent orders cannot reserve more than available stock.
- A failed message eventually reaches a DLQ after configured retries.
- Logs allow a reader to trace a single order across services using correlation/trace IDs.
- CI runs build and tests; deployment uses short-lived AWS credentials through OIDC.
- Terraform infrastructure can be planned and destroyed with explicit human review.

## 12. Out of scope for Release 1

- Real payment processing or PCI card-data handling.
- Frontend application.
- Kubernetes/EKS, Kafka, Redis, service mesh, CQRS/event sourcing, and multi-region active-active deployment.
- Complex promotions, shipping-carrier integration, tax calculation, returns portal, and multi-currency ledger.
- Production-scale high availability claims based only on a small demo deployment.

These can be documented as future enhancements, not silently added to the first milestone.
