# ADR 0001: Service boundaries

- **Status:** Proposed
- **Decision:** Keep Catalog, Order, Inventory, Payment, and Notification as separate services for Release 1. Each owns its domain data and independently deployable application.
- **Context:** FulfillX demonstrates independent service ownership while keeping the initial system small. Order alone decides order business status; other services publish facts from their domains.
- **Options:** Keep five services; combine Inventory with Order; combine Payment and Notification as sidecars or modules in Order.
- **Trade-offs:** Five services best demonstrate ownership and independent deployment but increase runtime, build, and operations overhead. Combining domains lowers overhead but weakens boundaries and can pull business decisions into Order.
