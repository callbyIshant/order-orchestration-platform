package io.fulfillx.order.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fulfillx.order.persistence.OrderOutboxStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OrderOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OrderOutboxPublisher.class);
    private final OrderOutboxStore outbox;
    private final OrderOutboxProperties settings;
    private final OrderAwsProperties aws;
    private final SqsClient sqs;
    private final SnsClient sns;
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<String, String> urls = new ConcurrentHashMap<>();

    public OrderOutboxPublisher(OrderOutboxStore outbox, OrderOutboxProperties settings, OrderAwsProperties aws,
                                SqsClient sqs, SnsClient sns, ObjectMapper mapper) {
        this.outbox = outbox; this.settings = settings; this.aws = aws; this.sqs = sqs; this.sns = sns; this.mapper = mapper;
    }

    @Scheduled(fixedDelayString = "${fulfillx.outbox.poll-ms:1000}")
    public void publish() {
        if (!settings.publisherEnabled()) return;
        for (var record : outbox.claim(settings.batchSize(), settings.leaseSeconds())) {
            try {
                Map<String, String> trace = traceValues(record.payload());
                if ("SNS".equals(record.destinationType())) {
                    String topic = record.destination().isBlank() ? aws.domainEventsTopicArn() : record.destination();
                    sns.publish(PublishRequest.builder().topicArn(topic).message(record.payload())
                            .messageAttributes(snsAttributes(trace)).build());
                } else {
                    String queue = urls.computeIfAbsent(record.destination(), name -> sqs.getQueueUrl(
                            GetQueueUrlRequest.builder().queueName(name).build()).queueUrl());
                    sqs.sendMessage(SendMessageRequest.builder().queueUrl(queue).messageBody(record.payload())
                            .messageAttributes(sqsAttributes(trace)).build());
                }
                outbox.markPublished(record.messageId(), record.leaseToken());
            } catch (Exception exception) {
                outbox.scheduleRetry(record.messageId(), record.leaseToken(), record.attempts(), settings.retrySeconds());
                log.warn("Order outbox publish failed for {}; scheduling retry", record.messageId(), exception);
            }
        }
    }

    private Map<String, String> traceValues(String body) throws Exception {
        var trace = mapper.readTree(body).path("traceContext");
        Map<String, String> result = new HashMap<>();
        trace.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asText()));
        return result;
    }

    private Map<String, software.amazon.awssdk.services.sns.model.MessageAttributeValue> snsAttributes(Map<String, String> values) {
        Map<String, software.amazon.awssdk.services.sns.model.MessageAttributeValue> result = new HashMap<>();
        values.forEach((key, value) -> result.put(key, software.amazon.awssdk.services.sns.model.MessageAttributeValue.builder()
                .dataType("String").stringValue(value).build()));
        return result;
    }

    private Map<String, software.amazon.awssdk.services.sqs.model.MessageAttributeValue> sqsAttributes(Map<String, String> values) {
        Map<String, software.amazon.awssdk.services.sqs.model.MessageAttributeValue> result = new HashMap<>();
        values.forEach((key, value) -> result.put(key, software.amazon.awssdk.services.sqs.model.MessageAttributeValue.builder()
                .dataType("String").stringValue(value).build()));
        return result;
    }
}
