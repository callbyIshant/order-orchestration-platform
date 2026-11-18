# Operations runbook

## Stuck saga

1. Find the order and inspect its workflow history and current status.
2. Search structured logs and traces by order `correlationId`; inspect saga age and outcome metrics.
3. Check the order-results, inventory-command, and payment-command queue age/depth plus the corresponding DLQ gauges.
4. Identify whether the last durable action is pending in an outbox, queued, or repeatedly failing in a consumer. Check service database connectivity and outbox retry timestamps.
5. Resolve the dependency or deploy a compatible fix, then follow the [messaging redrive procedure](messaging-redrive.md). Do not manually change saga status without a reviewed recovery procedure; compensating commands must remain idempotent.

## Message backlog or DLQ growth

Check `fulfillx.sqs.messages.visible`, `fulfillx.sqs.messages.inflight`, `fulfillx.sqs.oldest_message.age`, and DLQ queue gauges. Compare consumer failure counters and service logs by correlation ID. Verify LocalStack/AWS queue health and visibility timeout settings. Inspect a DLQ sample without deleting it, fix the cause, then use the documented redrive flow. Poison messages with invalid schema need a deliberate repair or discard decision; repeated redrive is not a fix.

## Database connection exhaustion

Use the database connection pool metrics and database-side active/idle connection counts. Check for stuck transactions, long queries, and service instance count versus pool maximum. Restore headroom by resolving leaked/stalled work or scaling the database connection limit only after checking capacity. Restarting an instance can release its connections but does not fix repeated leaks. Readiness goes down when the service database is unavailable; liveness remains process-only.

## Failed deployment

1. Stop rollout progression and compare the new release's health, startup logs, and migration state with the prior healthy version.
2. Check required OIDC issuer/JWKS/audience settings, database connectivity, IAM permissions, OTLP collector reachability, and queue configuration without printing secrets.
3. If startup or migration is incompatible, roll back to the last known compatible application artifact. Do not roll back schema changes destructively; use forward-compatible migrations and a reviewed repair migration.
4. Verify readiness, one authenticated request per role, metrics scraping, and trace export before resuming rollout.

## AWS telemetry operator setup

Configure CloudWatch Agent to scrape `/actuator/prometheus` over private service networking using a short-lived ADMIN access token and publish to namespace `FulfillX`. Configure ADOT Collector OTLP receivers and the AWS X-Ray exporter, with least-privilege IAM for telemetry writes. Store collector, agent, and IAM configuration in the deployment platform; the application has no AWS credentials embedded in files. Review token rotation and network access as part of deployment readiness.
