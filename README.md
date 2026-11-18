# FulfillX

## Order API (Milestone 4/5)

Order accepts authenticated customer requests at `POST /api/v1/orders`. Set `Idempotency-Key` to a unique customer-scoped value. The same normalized items and payment token replay the original accepted order for 24 hours; changing the request under an active key returns `409 IDEMPOTENCY_KEY_REUSED`. Product names and INR prices are read from Catalog before Order opens its write transaction. A missing or inactive product returns 422; a Catalog timeout or outage returns retryable 503.

Start local dependencies with `docker compose up -d` after copying `.env.example` to `.env`. The local Keycloak realm is available at `http://127.0.0.1:8080`; use the imported `demo-customer` account and request a token for client `fulfillx-api` with audience `fulfillx-api`. Keycloak admin and demo credentials are development-only values in `.env.example`. Do not use them outside local development. In AWS, set `FULFILLX_OIDC_PROVIDER=cognito`, the user-pool issuer URL, its JWKS URL, and the app client ID as `FULFILLX_OIDC_AUDIENCE` for all services. Every service validates signature, issuer, expiry, access-token type, and configured audience/client ID.

The API requires a bearer token with the `CUSTOMER` role. `POST` returns 202 with an order status URL; `GET /api/v1/orders/{orderId}` and `/history` are restricted to the owning token subject. Order creates a `ReserveInventory` command transactionally. Later Inventory and Payment results advance the Order-owned saga and write follow-up commands/events to the outbox. Delivery uses standard at-least-once SNS/SQS semantics; duplicates are recorded in the Order inbox. An unavailable downstream result can leave an order in its last nonterminal state until queue redrive/reconciliation is performed.

FulfillX is a cloud-native order management and fulfilment backend. It demonstrates independently deployable Java services coordinating an order workflow through asynchronous messaging, service-owned persistence, and recoverable saga processing. Release 1 uses simulated payments only and does not handle payment-card data.

## Services

| Service | Responsibility |
|---|---|
| Catalog | Product catalogue and pricing |
| Order | Orders, idempotency, workflow state, and saga orchestration |
| Inventory | Stock and reservations |
| Payment | Simulated authorizations and refunds |
| Notification | Order outcome notifications |

Each service is a separate Maven module and Spring Boot application. Services must not depend on another service's domain model. API and event contracts belong in `contracts/` as those interfaces are implemented.

## Core capabilities

- Product catalogue and price snapshots
- Asynchronous order creation and status tracking
- Concurrency-safe inventory reservations
- Simulated payment authorization and refund
- Saga orchestration with compensating actions
- Transactional outbox and idempotent message consumption
- Order notifications
- Local development and AWS deployment, introduced in later milestones

## Technology baseline

- Java 21 and Spring Boot 3.5.16
- Maven 3.10.1 through Maven Wrapper
- PostgreSQL and Flyway (later milestones)
- AWS SDK for Java 2.x, SNS, and SQS (later milestones)
- Docker Compose and LocalStack (later milestones)
- Terraform (later milestone)
- JUnit 5, Mockito, and Testcontainers (as tests are added)

## Requirements

- JDK 21
- A network connection on the first Maven Wrapper run to download Maven 3.10.1 and project dependencies

## Build

Validate the reactor:

```powershell
.\mvnw.cmd validate
```

Package one service and its parent configuration from the repository root:

```powershell
.\mvnw.cmd -pl services/catalog-service -am package
```

Replace `catalog-service` with `order-service`, `inventory-service`, `payment-service`, or `notification-service`. Each executable jar is written under that service's `target/` directory. Run it with:

```powershell
java -jar services/catalog-service/target/catalog-service-0.1.0-SNAPSHOT.jar
```

On macOS/Linux, use `./mvnw` in place of `./mvnw.cmd`.

## Architecture and decisions

- [Product and functional requirements](docs/PROJECT_CONTEXT.md)
- [Architecture and AWS deployment](docs/ARCHITECTURE.md)
- [Implementation roadmap](docs/ROADMAP.md)
- [ADR index](docs/adr/README.md)

## Development principles

1. Keep business logic and persistence with the service that owns the domain.
2. Use synchronous HTTP when an immediate response is required and messaging for workflow coordination.
3. Treat message delivery as at least once and make consumers idempotent.
4. Persist business changes and outgoing messages atomically with a transactional outbox.
5. Use database migrations for schema changes.
6. Never store real payment-card data or production secrets in the repository.
7. Review infrastructure plans before applying or destroying cloud resources.

## Repository layout

```text
fulfillx-git-ready/
├── services/
│   ├── catalog-service/
│   ├── order-service/
│   ├── inventory-service/
│   ├── payment-service/
│   └── notification-service/
├── docs/
│   └── adr/
├── pom.xml
├── mvnw / mvnw.cmd
└── .mvn/wrapper/
```

All five service modules have independent application entry points and service-owned security configuration.

## Catalog service

Catalog owns product and category data in its PostgreSQL database. Schema changes and repeatable demo data are applied by Flyway at startup; Hibernate validates the schema. The API returns DTOs and never exposes persistence entities. Prices are denominated in INR.

Start PostgreSQL and Mailpit locally (Catalog only needs PostgreSQL):

```powershell
Copy-Item .env.example .env # if you have not created your local .env yet
docker compose up -d --wait postgres
```

Run Catalog with its local-only profile from the repository root:

```powershell
.\mvnw.cmd -pl services/catalog-service -am spring-boot:run '-Dspring-boot.run.profiles=local'
```

The service listens on `http://127.0.0.1:8081`. Product GET routes are public; POST/PATCH require a valid bearer token with the `OPERATIONS` or `ADMIN` role. Local tokens come from Keycloak. In AWS configure the Cognito user-pool issuer, JWKS URL, and app client ID as described above.

API examples:

```text
GET   /api/v1/products?page=0&size=20&active=true&categoryId=10000000-0000-0000-0000-000000000001
GET   /api/v1/products/20000000-0000-0000-0000-000000000001
POST  /api/v1/products                 # bearer token with OPERATIONS or ADMIN
PATCH /api/v1/products/{id}            # bearer token with OPERATIONS or ADMIN
GET   /v3/api-docs
GET   /swagger-ui.html
```

Example create body:

```json
{
  "sku": "MUG-003",
  "name": "Insulated Travel Mug",
  "description": "Double-wall stainless steel, 400 ml.",
  "unitPrice": 799.00,
  "categoryId": "10000000-0000-0000-0000-000000000001",
  "active": true
}
```

Product lists default to active products, page 0, size 20; page sizes are limited to 100. PATCH leaves omitted or null fields unchanged; an empty description clears it. Responses include `currency: "INR"`. Errors use `application/problem+json` and include a stable `code`, request path, and correlation ID. Send a UUID in `X-Correlation-ID` to propagate one, or the service generates one. OpenAPI is served at `/v3/api-docs` and `/swagger-ui.html`.

Catalog migrations live under `services/catalog-service/src/main/resources/db/migration`: V1 creates categories, products, constraints, and filter indexes; V2 seeds three categories and five products (including one inactive product).

## Inventory service

Inventory owns stock quantities and reservations; it never reads Catalog or Order databases. `GET /api/v1/inventory/{productId}` returns on-hand, reserved, and available quantities. `PUT /api/v1/inventory/{productId}` sets on-hand quantity and rejects values below already reserved stock. Inventory reads and writes require a bearer token with `OPERATIONS` or `ADMIN`. Use the local Keycloak realm for development and Cognito JWTs in AWS.

Run it with PostgreSQL and LocalStack available:

```powershell
docker compose up -d --wait postgres localstack
.\mvnw.cmd -pl services/inventory-service -am spring-boot:run '-Dspring-boot.run.profiles=local'
```

The service consumes `ReserveInventory`, `ConfirmReservation`, and `ReleaseReservation` from `fulfillx-inventory-commands`; its outbox publishes reservation results to the `fulfillx-domain-events` SNS topic. Command/event payloads and routing are documented in `contracts/schemas/` and `contracts/asyncapi/inventory.yaml`. Duplicate SQS delivery and SNS publish ambiguity are handled as at-least-once delivery with a processed-message inbox and transactional outbox; commands exceeding the configured five receives are routed to the Inventory command DLQ.

Reservation schema is introduced by `services/inventory-service/src/main/resources/db/migration/V1__create_inventory_schema.sql`. Pessimistic stock-row locking in ascending product UUID order prevents overselling while preserving atomic multi-line reservation; see [ADR 0010](docs/adr/0010-inventory-concurrency.md).

## Payment and Notification services

Start PostgreSQL, LocalStack, and Mailpit, then run Payment and Notification in separate terminals:

```powershell
docker compose up -d --wait postgres localstack mailpit
.\mvnw.cmd -pl services/payment-service -am spring-boot:run '-Dspring-boot.run.profiles=local'
```

```powershell
.\mvnw.cmd -pl services/notification-service -am spring-boot:run '-Dspring-boot.run.profiles=local'
```

Payment consumes `AuthorizePayment` and `RefundPayment`; it stores only the simulated decision and payment state. `tok_success` authorizes, `tok_decline` declines, and any unsupported token value is declined as invalid. Notification consumes final Order events and sends through its adapter to Mailpit by default. Messages use an order-specific synthetic `@example.invalid` recipient and contain no customer email or payment token. Inspect local messages at `http://127.0.0.1:8025`.

The Payment and Notification schemas are in `contracts/schemas/`; routing is documented in `contracts/asyncapi/payment.yaml` and `contracts/asyncapi/notification.yaml`. Outbox publishing uses capped exponential backoff with jitter. Consumers validate schema version and required fields, set a 30-second initial visibility timeout, and leave failed work for bounded redrive to the configured DLQ. See [the messaging redrive runbook](docs/runbooks/messaging-redrive.md).

## Local development dependencies

The development stack is defined in [`compose.yaml`](compose.yaml). It exposes services only on loopback: PostgreSQL at `127.0.0.1:5432`, LocalStack at `127.0.0.1:4566`, Mailpit SMTP at `127.0.0.1:1025`, and the Mailpit UI at `http://127.0.0.1:8025`.

Copy the local defaults and review them before starting:

```powershell
Copy-Item .env.example .env
```

The database and AWS values in `.env.example` are public, local-only development defaults. Do not reuse them outside this workstation. Add your own LocalStack developer auth token to `.env`; it is intentionally blank in the example and must never be committed. [Current LocalStack images require an auth token](https://docs.localstack.cloud/aws/getting-started/auth-token/) to activate AWS services.

Validate the Compose model and start the full stack:

```powershell
docker compose config
.\scripts\dev\up.ps1
```

`up.ps1` waits for the LocalStack token before starting and waits for all health checks. If you only need PostgreSQL and Mailpit, start them without LocalStack using `docker compose up -d --wait postgres mailpit`. Stop services while preserving named-volume data with:

```powershell
.\scripts\dev\down.ps1
```

To reset the local databases, messages, and emulator state, use the destructive script below. It prompts before removing the three named volumes; `-WhatIf` previews the action. Do not run it unless you approve deleting all local data.

```powershell
.\scripts\dev\reset.ps1 -Confirm
```

### PostgreSQL ownership

The initialization hook creates a separate database and login role for each service. Each application role owns its database, can connect only to that database, and is not a superuser or allowed to create databases. `fulfillx_admin` is only the local bootstrap/admin account.

| Service | Database | User | Password variable |
|---|---|---|---|
| Catalog | `catalog_db` | `catalog_user` | `CATALOG_DB_PASSWORD` |
| Order | `order_db` | `order_user` | `ORDER_DB_PASSWORD` |
| Inventory | `inventory_db` | `inventory_user` | `INVENTORY_DB_PASSWORD` |
| Payment | `payment_db` | `payment_user` | `PAYMENT_DB_PASSWORD` |
| Notification | `notification_db` | `notification_user` | `NOTIFICATION_DB_PASSWORD` |

Connect host-run applications to `localhost:<POSTGRES_PORT>` and their own database/user. Database initialization scripts run only when the PostgreSQL named volume is empty; changing `.env` credentials later does not rewrite existing roles. Use the confirmed destructive reset only when you intend to discard that volume. If your machine already uses port `5432`, set `POSTGRES_PORT` to another free loopback port in `.env`.

### SNS/SQS resources

The LocalStack ready hook idempotently creates the first-saga messaging topology:

| Resource | Purpose |
|---|---|
| SNS `fulfillx-domain-events` | Inventory, Payment, and Order domain events |
| SQS `fulfillx-inventory-commands` → `fulfillx-inventory-commands-dlq` | Order sends reserve, confirm, and release commands to Inventory |
| SQS `fulfillx-payment-commands` → `fulfillx-payment-commands-dlq` | Order sends authorize and refund commands to Payment |
| SQS `fulfillx-order-results` → `fulfillx-order-results-dlq` | SNS subscription filtered to inventory and payment result events consumed by Order |
| SQS `fulfillx-notification-events` → `fulfillx-notification-events-dlq` | SNS subscription filtered to `OrderConfirmed`, `OrderCancelled`, and `OrderRejected` |

Every source queue uses a redrive policy with `maxReceiveCount=5`. SNS subscriptions allow delivery only from the FulfillX topic, use raw message delivery, and filter on the JSON envelope’s `messageType` field. The SQS queues are standard queues: delivery is at least once, so consumers must be idempotent. LocalStack resources persist in the named volume. The AWS SDK clients should use standard AWS SDK v2 `SnsClient`/`SqsClient` configuration with `AWS_REGION`, the fake local credentials, and the configured `AWS_ENDPOINT_URL`; service code must not use LocalStack-specific Java APIs.

### Mailpit

Configure local notification delivery to SMTP `localhost:<MAILPIT_SMTP_PORT>` and inspect captured mail in the Mailpit UI. Its SQLite database is stored in the `mailpit-data` named volume so captured messages survive container recreation ([Mailpit persistence](https://mailpit.axllent.org/docs/configuration/email-storage/), [health checks](https://mailpit.axllent.org/docs/integration/healthcheck/)).

Notification messages use order-specific synthetic recipients such as `order-<orderId>@example.invalid`; they do not represent customer email addresses. Payment accepts only `tok_success` and `tok_decline` as recognized simulation tokens; unknown values are declined as invalid tokens. No card data or real payment provider is used. For DLQ inspection and redrive, see [the messaging runbook](docs/runbooks/messaging-redrive.md).

The Order UUID starts the correlation chain. Each command and result keeps that `correlationId`, while `causationId` identifies the immediately preceding message. Notification delivery records and mail headers retain the same correlation ID for tracing.

## Security and observability

All five services validate bearer JWTs using configured issuer and JWKS URLs. Role claims map only `CUSTOMER`, `OPERATIONS`, and `ADMIN`; Keycloak realm roles and Cognito groups are supported. Order endpoints require CUSTOMER and scope reads to the JWT subject. Catalog and Inventory writes require OPERATIONS or ADMIN. Health endpoints are public; other Actuator endpoints require ADMIN. Create a local operations/admin user in Keycloak and assign the corresponding realm role before calling protected routes. The bundled demo user has only CUSTOMER access.

For AWS, set `FULFILLX_OIDC_PROVIDER=cognito`, `FULFILLX_OIDC_ISSUER_URI=https://cognito-idp.<region>.amazonaws.com/<user-pool-id>`, `FULFILLX_OIDC_JWK_SET_URI=https://cognito-idp.<region>.amazonaws.com/<user-pool-id>/.well-known/jwks.json`, and `FULFILLX_OIDC_AUDIENCE=<app-client-id>` through the deployment secret/configuration system. Do not commit these deployment values. Cognito tokens must be access tokens.

Spring Boot emits ECS JSON logs with service, trace, span, and correlation fields. All services expose `/actuator/prometheus`; local Prometheus is at `http://127.0.0.1:9090`. It scrapes services every 15 seconds using a bearer token read from `.local-data/prometheus/admin-token`. Create this ignored file with a short-lived local ADMIN access token; refresh it when it expires and restart Prometheus to pick up the new token. Jaeger with OTLP ingest is available at `http://127.0.0.1:16686`; local service profiles export traces to `127.0.0.1:4318`. Start both with `docker compose up -d prometheus jaeger`.

In AWS, configure CloudWatch Agent to scrape each service's Prometheus endpoint over private networking and publish metrics to CloudWatch under the `FulfillX` namespace. Configure the AWS Distro for OpenTelemetry Collector (ADOT) to receive OTLP from services and export traces to AWS X-Ray. Supply collector/agent configuration, IAM permissions, and endpoints through the deployment environment; no credentials belong in application configuration. Liveness reflects process health. Readiness includes database connectivity; broker and email outages rely on durable outbox/retry behavior and do not remove otherwise healthy APIs from service.

See [the operations runbook](docs/runbooks/operations.md) for stuck saga, queue backlog/DLQ, database pool exhaustion, and deployment recovery.

