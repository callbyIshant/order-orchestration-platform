# ADR 0006: Database ownership

- **Status:** Proposed
- **Decision:** Each service owns its schema, credentials, tables, and migrations; no cross-service JPA models or database joins. A low-cost demo may use separate databases/users on one RDS instance.
- **Context:** Independent service ownership must not be undermined by shared persistence. The project has a portfolio/demo cost constraint.
- **Options:** Separate database instances; separate databases/users on one instance; one shared schema/database.
- **Trade-offs:** Separate instances improve isolation but cost more. A shared instance reduces cost while retaining logical separation, but it shares failure, capacity, and maintenance boundaries. A shared schema weakens ownership and is not proposed.
