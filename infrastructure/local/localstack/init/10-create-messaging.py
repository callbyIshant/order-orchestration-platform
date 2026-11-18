import json
import os

import boto3


endpoint = "http://localhost:4566"
region = os.environ.get("AWS_DEFAULT_REGION", "us-east-1")
sns = boto3.client("sns", endpoint_url=endpoint, region_name=region)
sqs = boto3.client("sqs", endpoint_url=endpoint, region_name=region)

topic_name = os.environ.get("FULFILLX_DOMAIN_EVENTS_TOPIC", "fulfillx-domain-events")
topic_arn = sns.create_topic(Name=topic_name)["TopicArn"]


def ensure_queue(name, dead_letter_name=None):
    attributes = {}
    if dead_letter_name:
        dead_letter_url = sqs.create_queue(QueueName=dead_letter_name)["QueueUrl"]
        dead_letter_arn = sqs.get_queue_attributes(
            QueueUrl=dead_letter_url, AttributeNames=["QueueArn"]
        )["Attributes"]["QueueArn"]
        attributes["RedrivePolicy"] = json.dumps(
            {"deadLetterTargetArn": dead_letter_arn, "maxReceiveCount": "5"}
        )
    queue_url = sqs.create_queue(QueueName=name, Attributes=attributes)["QueueUrl"]
    queue_arn = sqs.get_queue_attributes(
        QueueUrl=queue_url, AttributeNames=["QueueArn"]
    )["Attributes"]["QueueArn"]
    return queue_url, queue_arn


def subscribe_queue(queue_name, dead_letter_name, message_types):
    queue_url, queue_arn = ensure_queue(queue_name, dead_letter_name)
    policy = {
        "Version": "2012-10-17",
        "Statement": [
            {
                "Sid": "AllowFulfillXDomainEvents",
                "Effect": "Allow",
                "Principal": {"Service": "sns.amazonaws.com"},
                "Action": "sqs:SendMessage",
                "Resource": queue_arn,
                "Condition": {"ArnEquals": {"aws:SourceArn": topic_arn}},
            }
        ],
    }
    sqs.set_queue_attributes(
        QueueUrl=queue_url, Attributes={"Policy": json.dumps(policy)}
    )

    subscriptions = sns.list_subscriptions_by_topic(TopicArn=topic_arn)[
        "Subscriptions"
    ]
    subscription = next(
        (
            item
            for item in subscriptions
            if item.get("Protocol") == "sqs" and item.get("Endpoint") == queue_arn
        ),
        None,
    )
    attributes = {
        "RawMessageDelivery": "true",
        "FilterPolicyScope": "MessageBody",
        "FilterPolicy": json.dumps({"messageType": message_types}),
    }
    if subscription:
        subscription_arn = subscription["SubscriptionArn"]
        for key, value in attributes.items():
            sns.set_subscription_attributes(
                SubscriptionArn=subscription_arn, AttributeName=key, AttributeValue=value
            )
    else:
        sns.subscribe(
            TopicArn=topic_arn,
            Protocol="sqs",
            Endpoint=queue_arn,
            Attributes=attributes,
            ReturnSubscriptionArn=True,
        )
    print(f"Configured {queue_name} subscription and {dead_letter_name}")


ensure_queue(
    os.environ.get("INVENTORY_COMMANDS_QUEUE", "fulfillx-inventory-commands"),
    os.environ.get("INVENTORY_COMMANDS_DLQ", "fulfillx-inventory-commands-dlq"),
)
ensure_queue(
    os.environ.get("PAYMENT_COMMANDS_QUEUE", "fulfillx-payment-commands"),
    os.environ.get("PAYMENT_COMMANDS_DLQ", "fulfillx-payment-commands-dlq"),
)
subscribe_queue(
    os.environ.get("ORDER_RESULTS_QUEUE", "fulfillx-order-results"),
    os.environ.get("ORDER_RESULTS_DLQ", "fulfillx-order-results-dlq"),
    [
        "InventoryReserved",
        "InventoryRejected",
        "InventoryCommitted",
        "InventoryCommitRejected",
        "ReservationReleased",
        "PaymentAuthorized",
        "PaymentDeclined",
        "PaymentRefunded",
    ],
)
subscribe_queue(
    os.environ.get("NOTIFICATION_EVENTS_QUEUE", "fulfillx-notification-events"),
    os.environ.get("NOTIFICATION_EVENTS_DLQ", "fulfillx-notification-events-dlq"),
    ["OrderConfirmed", "OrderCancelled", "OrderRejected"],
)
print(f"Configured SNS topic {topic_name}")
