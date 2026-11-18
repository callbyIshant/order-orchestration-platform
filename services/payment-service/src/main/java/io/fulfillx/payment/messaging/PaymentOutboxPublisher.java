package io.fulfillx.payment.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fulfillx.payment.persistence.PaymentOutboxStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.HashMap;
import java.util.Map;

@Component
public class PaymentOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(PaymentOutboxPublisher.class);
    private final PaymentOutboxStore store;
    private final PaymentProperties properties;
    private final SnsClient sns;
    private final ObjectMapper mapper;

    public PaymentOutboxPublisher(PaymentOutboxStore store, PaymentProperties properties, SnsClient sns, ObjectMapper mapper) {
        this.store = store; this.properties = properties; this.sns = sns; this.mapper = mapper;
    }

    @Scheduled(fixedDelayString = "${payment.outbox.poll-ms:1000}")
    public void publish() {
        if (!properties.outbox().publisherEnabled()) return;
        String topic = properties.messaging().topicArn();
        if (topic == null || topic.isBlank()) {
            log.error("FULFILLX_DOMAIN_EVENTS_TOPIC_ARN is required for Payment events");
            return;
        }
        for (var row : store.claim(properties.outbox().batchSize(), properties.outbox().leaseSeconds())) {
            try {
                sns.publish(PublishRequest.builder().topicArn(topic).message(row.payload())
                        .messageAttributes(traceAttributes(row.payload())).build());
                store.published(row.messageId(), row.leaseToken());
            } catch (Exception ex) {
                store.retry(row.messageId(), row.leaseToken(), row.attempts(), properties.outbox().retrySeconds());
                log.warn("Payment outbox publish failed for {}; scheduling retry", row.messageId(), ex);
            }
        }
    }

    private Map<String, MessageAttributeValue> traceAttributes(String body) throws Exception {
        var trace = mapper.readTree(body).path("traceContext"); Map<String, MessageAttributeValue> result = new HashMap<>();
        trace.fields().forEachRemaining(entry -> result.put(entry.getKey(), MessageAttributeValue.builder()
                .dataType("String").stringValue(entry.getValue().asText()).build()));
        return result;
    }
}

