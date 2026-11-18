# Messaging failure inspection and redrive

FulfillX uses standard SNS/SQS delivery and at-least-once processing. Every source queue has a configured DLQ and `maxReceiveCount=5`. Consumers start with a 30-second visibility timeout and, on failure, request a randomized delay that grows exponentially from 2 seconds to a 30-second cap. Keep handlers below the visibility timeout; Notification SMTP timeouts are 3 seconds and its delivery lease is 120 seconds. A DLQ message is evidence to inspect, not an instruction to delete it. Outbox publication keeps retrying with exponential jitter and a 5-minute delay cap so messages remain durable through longer SNS/SQS outages.

## Trace the message

1. Capture the source queue name, DLQ name, approximate message count, and receive time.
2. Read a message without deleting it. Record `messageId`, `messageType`, `schemaVersion`, `correlationId`, `causationId`, and the safe error code. Do not copy payment tokens, credentials, or full access tokens into an incident ticket.
3. Search service logs by `correlationId` and message ID. The first Order command uses the order UUID as correlation ID. Each service preserves that correlation ID and sets the next envelope's `causationId` to the immediately handled message ID.
4. Correct the transient dependency or deploy a backward-compatible consumer fix. For an invalid schema or permanently unsupported message, inspect the contract and decide whether to discard it or repair/re-publish a corrected event; do not blindly redrive a poison message.

## LocalStack redrive

Use the AWS CLI configured with the local test credentials and `--endpoint-url http://127.0.0.1:4566`.

1. Find a queue URL with `aws --endpoint-url http://127.0.0.1:4566 sqs get-queue-url --queue-name <queue-name>`.
2. Inspect its `RedrivePolicy` and `ApproximateNumberOfMessages` using `get-queue-attributes`; inspect a sample with `receive-message` and `--attribute-names ApproximateReceiveCount` without deleting it.
3. After correcting the cause, obtain the DLQ ARN with `get-queue-attributes --attribute-names QueueArn` and call `start-message-move-task --source-arn <dlq-arn>`. Monitor source and DLQ message counts until the move and consumer processing complete.
4. If a notification send failed, inspect the notification and `delivery_attempts` rows first. Redrive the same event from the Notification DLQ after Mailpit/provider connectivity is restored; a delivered record is acknowledged without sending again. A crash after SMTP accepts the email but before the database records success can still result in a repeated email.

In production, use the AWS console/CLI with the service operator role, preserve the original message ID, and record the reason and operator in the change log. Never purge a queue or DLQ as a substitute for inspecting it.
