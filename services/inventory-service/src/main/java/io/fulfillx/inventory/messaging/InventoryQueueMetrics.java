package io.fulfillx.inventory.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class InventoryQueueMetrics {
    private static final Logger log=LoggerFactory.getLogger(InventoryQueueMetrics.class);
    private final SqsClient sqs;
    private final String source;
    private final String dlq;
    private final AtomicLong sourceVisible=new AtomicLong(), sourceInflight=new AtomicLong(), sourceAge=new AtomicLong(), dlqVisible=new AtomicLong(), dlqInflight=new AtomicLong(), dlqAge=new AtomicLong();

    public InventoryQueueMetrics(SqsClient sqs, MeterRegistry meters,
                                 @Value("${inventory.messaging.queue-name}") String source,
                                 @Value("${inventory.messaging.dlq-name}") String dlq) {
        this.sqs=sqs;this.source=source;this.dlq=dlq;
        gauge(meters,"inventory-commands","visible",sourceVisible);gauge(meters,"inventory-commands","inflight",sourceInflight);gauge(meters,"inventory-commands","age",sourceAge);
        gauge(meters,"inventory-commands-dlq","visible",dlqVisible);gauge(meters,"inventory-commands-dlq","inflight",dlqInflight);gauge(meters,"inventory-commands-dlq","age",dlqAge);
    }

    @Scheduled(fixedDelayString="${fulfillx.queue-metrics.poll-ms:30000}",initialDelayString="${fulfillx.queue-metrics.initial-delay-ms:10000}")
    public void poll(){poll(source,sourceVisible,sourceInflight,sourceAge);poll(dlq,dlqVisible,dlqInflight,dlqAge);}

    private void poll(String name,AtomicLong visible,AtomicLong inflight,AtomicLong age){
        try{String url=sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build()).queueUrl();var attrs=sqs.getQueueAttributes(GetQueueAttributesRequest.builder().queueUrl(url).attributeNamesWithStrings("ApproximateNumberOfMessages","ApproximateNumberOfMessagesNotVisible","ApproximateAgeOfOldestMessage").build()).attributesAsStrings();visible.set(number(attrs.get("ApproximateNumberOfMessages")));inflight.set(number(attrs.get("ApproximateNumberOfMessagesNotVisible")));age.set(number(attrs.get("ApproximateAgeOfOldestMessage")));}
        catch(RuntimeException exception){log.debug("SQS queue metrics unavailable for queue role {}",name.equals(source)?"source":"dlq",exception);}
    }
    private static long number(String value){try{return Long.parseLong(value);}catch(Exception ignored){return 0;}}
    private static void gauge(MeterRegistry meters,String queue,String value,AtomicLong amount){meters.gauge("fulfillx.sqs.messages."+value,Tags.of("service","inventory-service","queue",queue),amount,AtomicLong::get);}
}

