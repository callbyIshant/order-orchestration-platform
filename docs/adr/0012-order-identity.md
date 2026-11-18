# ADR 0012: Customer identity at the Order API

- **Status:** Accepted
- **Context:** Order creation and reads must be attributable to a customer, and the local Basic Auth used by Catalog and Inventory is unsuitable for customer access.
- **Decision:** Order is an OAuth 2.0 JWT resource server. It validates issuer, JWK signature, and `fulfillx-api` audience, reads the customer identifier from `sub`, and requires `CUSTOMER` role for the Order API. The local Compose profile imports a development-only Keycloak realm and demo customer; production issuer and JWK URIs are environment configuration.
- **Options:** Local Basic Auth (does not represent customer identity); self-issued opaque tokens (custom lifecycle and validation); JWT resource server (standard issuer trust and role claims).
- **Trade-offs:** Keycloak is an additional local dependency. JWT validation is offline per request after key retrieval; role claim mapping currently accommodates Keycloak realm roles and common `roles`/`cognito:groups` claims.
