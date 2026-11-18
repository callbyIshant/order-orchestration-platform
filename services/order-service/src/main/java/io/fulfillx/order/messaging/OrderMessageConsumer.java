package io.fulfillx.order.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fulfillx.observability.TraceContextCarrier;
import io.fulfillx.order.application.OrderSagaService;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class OrderMessageConsumer {
    private static final Logger log = LoggerFactory.getLogger(OrderMessageConsumer.class);
    private final SqsClient sqs;
    private final OrderAwsProperties aws;
    private final OrderMessagingProperties settings;
    private final OrderSagaService saga;
    private final ObjectMapper mapper;
    private final OpenTelemetry telemetry;
    private final io.micrometer.core.instrument.Counter succeeded;
    private final io.micrometer.core.instrument.Counter failed;
    private final AtomicReference<String> queueUrl = new AtomicReference<>();

    public OrderMessageConsumer(SqsClient sqs, OrderAwsProperties aws, OrderMessagingProperties settings,
                                OrderSagaService saga, ObjectMapper mapper, OpenTelemetry telemetry, MeterRegistry meters) {
        this.sqs=sqs; this.aws=aws; this.settings=settings; this.saga=saga; this.mapper=mapper; this.telemetry=telemetry;
        succeeded=meters.counter("fulfillx.consumer.messages", "service", "order-service", "outcome", "success");
        failed=meters.counter("fulfillx.consumer.messages", "service", "order-service", "outcome", "failure");
    }

    @Scheduled(fixedDelayString = "${fulfillx.messaging.poll-ms:1000}")
    public void poll() {
        if (!settings.consumerEnabled()) return;
        try {
            String url=queueUrl.updateAndGet(old -> old==null ? sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(aws.orderResultsQueue()).build()).queueUrl() : old);
            var response=sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(url).waitTimeSeconds(10).visibilityTimeout(30)
                    .maxNumberOfMessages(10).messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)
                    .messageAttributeNames("All").build());
            for (var message : response.messages()) process(url, message);
        } catch (RuntimeException exception) {
            log.warn("Could not poll the Order result queue; it will be retried", exception);
        }
    }

    private void process(String url, Message message) {
        JsonNode body;
        try { body=mapper.readTree(message.body()); } catch (Exception ignored) { body=mapper.createObjectNode(); }
        String correlation=body.path("correlationId").asText(null);
        Map<String,String> carrier=new HashMap<>();
        message.messageAttributes().forEach((name,value)->{if(value.stringValue()!=null)carrier.put(name,value.stringValue());});
        Context parent=TraceContextCarrier.extract(telemetry, carrier);
        String previous=MDC.get("correlationId");
        try (Scope scope=parent.makeCurrent()) {
            if (correlation!=null) MDC.put("correlationId",correlation);
            try {
                saga.accept(mapper.treeToValue(body, OrderMessageEnvelope.class));
                sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(url).receiptHandle(message.receiptHandle()).build());
                succeeded.increment();
            } catch (Exception exception) {
                failed.increment();
                int count=receiveCount(message.attributesAsStrings().get("ApproximateReceiveCount"));
                int cap=Math.min(settings.visibilityMaxSeconds(),settings.visibilityBaseSeconds()*(1<<Math.min(10,count-1)));
                int delay=ThreadLocalRandom.current().nextInt(Math.max(1,cap/2),Math.max(2,cap+1));
                sqs.changeMessageVisibility(ChangeMessageVisibilityRequest.builder().queueUrl(url).receiptHandle(message.receiptHandle()).visibilityTimeout(delay).build());
                log.warn("Order result processing failed; attempt {} will retry or redrive",count,exception);
            }
        } finally { if(previous==null)MDC.remove("correlationId");else MDC.put("correlationId",previous); }
    }

    private static int receiveCount(String value) { try { return Math.max(1,Integer.parseInt(value)); } catch (Exception ignored) { return 1; } }
}
