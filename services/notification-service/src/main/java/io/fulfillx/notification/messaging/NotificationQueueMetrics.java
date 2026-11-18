package io.fulfillx.notification.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component public class NotificationQueueMetrics {
    private static final Logger log=LoggerFactory.getLogger(NotificationQueueMetrics.class);
    private final SqsClient sqs;private final String source,dlq;
    private final AtomicLong sourceVisible=new AtomicLong(),sourceInflight=new AtomicLong(),sourceAge=new AtomicLong(),dlqVisible=new AtomicLong(),dlqInflight=new AtomicLong(),dlqAge=new AtomicLong();
    public NotificationQueueMetrics(SqsClient sqs,MeterRegistry meters,@Value("${notification.messaging.queue-name}")String source,@Value("${notification.messaging.dlq-name}")String dlq){this.sqs=sqs;this.source=source;this.dlq=dlq;gauge(meters,"notification-events",sourceVisible);inflightGauge(meters,"notification-events",sourceInflight);ageGauge(meters,"notification-events",sourceAge);gauge(meters,"notification-events-dlq",dlqVisible);inflightGauge(meters,"notification-events-dlq",dlqInflight);ageGauge(meters,"notification-events-dlq",dlqAge);}
    @Scheduled(fixedDelayString="${fulfillx.queue-metrics.poll-ms:30000}",initialDelayString="${fulfillx.queue-metrics.initial-delay-ms:10000}") public void poll(){poll(source,sourceVisible,sourceInflight,sourceAge);poll(dlq,dlqVisible,dlqInflight,dlqAge);}
    private void poll(String name,AtomicLong visible,AtomicLong inflight,AtomicLong age){try{String url=sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build()).queueUrl();var a=sqs.getQueueAttributes(GetQueueAttributesRequest.builder().queueUrl(url).attributeNamesWithStrings("ApproximateNumberOfMessages","ApproximateNumberOfMessagesNotVisible","ApproximateAgeOfOldestMessage").build()).attributesAsStrings();visible.set(n(a.get("ApproximateNumberOfMessages")));inflight.set(n(a.get("ApproximateNumberOfMessagesNotVisible")));age.set(n(a.get("ApproximateAgeOfOldestMessage")));}catch(RuntimeException ex){log.debug("SQS queue metrics unavailable for role {}",name.equals(source)?"source":"dlq",ex);}}
    private static long n(String v){try{return Long.parseLong(v);}catch(Exception ignored){return 0;}}
    private static void gauge(MeterRegistry r,String queue,AtomicLong value){r.gauge("fulfillx.sqs.messages.visible",Tags.of("service","notification-service","queue",queue),value,AtomicLong::get);}
    private static void inflightGauge(MeterRegistry r,String queue,AtomicLong value){r.gauge("fulfillx.sqs.messages.inflight",Tags.of("service","notification-service","queue",queue),value,AtomicLong::get);}
    private static void ageGauge(MeterRegistry r,String queue,AtomicLong value){r.gauge("fulfillx.sqs.oldest_message.age",Tags.of("service","notification-service","queue",queue),value,AtomicLong::get);}
}

