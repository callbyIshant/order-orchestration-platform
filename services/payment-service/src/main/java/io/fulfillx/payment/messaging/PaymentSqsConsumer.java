package io.fulfillx.payment.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fulfillx.observability.TraceContextCarrier;
import io.micrometer.core.instrument.Counter;
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
public class PaymentSqsConsumer {
    private static final Logger log=LoggerFactory.getLogger(PaymentSqsConsumer.class);
    private final SqsClient sqs;
    private final PaymentProperties properties;
    private final PaymentCommandRouter router;
    private final ObjectMapper mapper;
    private final OpenTelemetry telemetry;
    private final Counter succeeded;
    private final Counter failed;
    private final AtomicReference<String> queue=new AtomicReference<>();

    public PaymentSqsConsumer(SqsClient sqs, PaymentProperties properties, PaymentCommandRouter router,
                              ObjectMapper mapper, OpenTelemetry telemetry, MeterRegistry meters) {
        this.sqs=sqs; this.properties=properties; this.router=router; this.mapper=mapper; this.telemetry=telemetry;
        succeeded=meters.counter("fulfillx.consumer.messages","service","payment-service","outcome","success");
        failed=meters.counter("fulfillx.consumer.messages","service","payment-service","outcome","failure");
    }

    @Scheduled(fixedDelayString="${payment.poll-ms:1000}")
    public void poll() {
        if(!properties.messaging().consumerEnabled())return;
        try {
            String url=queue.updateAndGet(old->old==null?sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(properties.messaging().queueName()).build()).queueUrl():old);
            var result=sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(url).waitTimeSeconds(10).visibilityTimeout(30).maxNumberOfMessages(10)
                    .messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT).messageAttributeNames("All").build());
            for(var message:result.messages())process(url,message);
        } catch(RuntimeException exception){log.warn("Payment command queue poll failed",exception);}
    }

    private void process(String url,Message message) {
        JsonNode body; try{body=mapper.readTree(message.body());}catch(Exception ignored){body=mapper.createObjectNode();}
        String correlation=body.path("correlationId").asText(null);
        Map<String,String> carrier=new HashMap<>(); message.messageAttributes().forEach((name,value)->{if(value.stringValue()!=null)carrier.put(name,value.stringValue());});
        Context parent=TraceContextCarrier.extract(telemetry,carrier); String previous=MDC.get("correlationId");
        try(Scope scope=parent.makeCurrent()) {
            if(correlation!=null)MDC.put("correlationId",correlation);
            try{router.handle(message.body());sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(url).receiptHandle(message.receiptHandle()).build());succeeded.increment();}
            catch(RuntimeException exception){failed.increment();int count=receiveCount(message.attributesAsStrings().get("ApproximateReceiveCount"));int cap=Math.min(properties.visibilityMaxSeconds(),properties.visibilityBaseSeconds()*(1<<Math.min(10,count-1)));int delay=ThreadLocalRandom.current().nextInt(Math.max(1,cap/2),Math.max(2,cap+1));sqs.changeMessageVisibility(ChangeMessageVisibilityRequest.builder().queueUrl(url).receiptHandle(message.receiptHandle()).visibilityTimeout(delay).build());log.warn("Payment command failed; attempt {} will retry or redrive",count,exception);}
        } finally {if(previous==null)MDC.remove("correlationId");else MDC.put("correlationId",previous);}
    }

    private static int receiveCount(String value){try{return Math.max(1,Integer.parseInt(value));}catch(Exception ignored){return 1;}}
}
