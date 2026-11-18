package io.fulfillx.inventory.messaging;

import io.fulfillx.inventory.persistence.OutboxStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import java.util.HashMap;
import java.util.Map;

@Component
public class InventoryOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(InventoryOutboxPublisher.class);
    private final OutboxStore outbox;
    private final SnsClient sns;
    private final InventoryOutboxProperties properties;
    private final InventoryMessagingProperties messaging;
    private final ObjectMapper mapper;

    public InventoryOutboxPublisher(OutboxStore outbox, SnsClient sns, InventoryOutboxProperties properties, InventoryMessagingProperties messaging, ObjectMapper mapper) {
        this.outbox = outbox; this.sns = sns; this.properties = properties; this.messaging = messaging; this.mapper=mapper;
    }

    @Scheduled(fixedDelayString = "${inventory.outbox.poll-ms:1000}")
    public void publishPending() {
        if (!properties.publisherEnabled()) return;
        String topicArn = messaging.topicArn();
        if (topicArn == null || topicArn.isBlank()) {
            log.error("FULFILLX_DOMAIN_EVENTS_TOPIC_ARN is required to publish Inventory results");
            return;
        }
        for (OutboxMessage message : outbox.claim(properties.batchSize(), properties.leaseSeconds())) {
            try {
                sns.publish(PublishRequest.builder().topicArn(topicArn).message(message.payload()).messageAttributes(traceAttributes(message.payload())).build());
                outbox.markPublished(message.messageId(), message.leaseToken());
            } catch (Exception exception) {
                outbox.scheduleRetry(message.messageId(), message.leaseToken(), message.attempts(), properties.retrySeconds());
                log.warn("Inventory outbox publish failed for {}; scheduling retry", message.messageId(), exception);
            }
        }
    }

    private Map<String,MessageAttributeValue> traceAttributes(String body) throws Exception {
        var trace=mapper.readTree(body).path("traceContext"); Map<String,MessageAttributeValue> result=new HashMap<>();
        trace.fields().forEachRemaining(entry -> result.put(entry.getKey(),MessageAttributeValue.builder().dataType("String").stringValue(entry.getValue().asText()).build()));
        return result;
    }
}

