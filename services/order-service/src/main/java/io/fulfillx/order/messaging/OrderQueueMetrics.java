package io.fulfillx.order.messaging;

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

@Component public class OrderQueueMetrics {
    private static final Logger log=LoggerFactory.getLogger(OrderQueueMetrics.class);
    private final SqsClient sqs;private final String source,dlq;
    private final AtomicLong sourceVisible=new AtomicLong(),sourceInflight=new AtomicLong(),sourceAge=new AtomicLong(),dlqVisible=new AtomicLong(),dlqInflight=new AtomicLong(),dlqAge=new AtomicLong();
    public OrderQueueMetrics(SqsClient sqs,MeterRegistry meters,@Value("${fulfillx.aws.order-results-queue}")String source,@Value("${fulfillx.aws.order-results-dlq}")String dlq){this.sqs=sqs;this.source=source;this.dlq=dlq;gauge(meters,"order-results",sourceVisible,"visible");gauge(meters,"order-results",sourceInflight,"inflight");gauge(meters,"order-results",sourceAge,"age");gauge(meters,"order-results-dlq",dlqVisible,"visible");gauge(meters,"order-results-dlq",dlqInflight,"inflight");gauge(meters,"order-results-dlq",dlqAge,"age");}
    @Scheduled(fixedDelayString="${fulfillx.queue-metrics.poll-ms:30000}",initialDelayString="${fulfillx.queue-metrics.initial-delay-ms:10000}") public void poll(){poll(source,sourceVisible,sourceInflight,sourceAge);poll(dlq,dlqVisible,dlqInflight,dlqAge);}
    private void poll(String name,AtomicLong visible,AtomicLong inflight,AtomicLong age){try{String url=sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build()).queueUrl();var a=sqs.getQueueAttributes(GetQueueAttributesRequest.builder().queueUrl(url).attributeNamesWithStrings("ApproximateNumberOfMessages","ApproximateNumberOfMessagesNotVisible","ApproximateAgeOfOldestMessage").build()).attributesAsStrings();visible.set(n(a.get("ApproximateNumberOfMessages")));inflight.set(n(a.get("ApproximateNumberOfMessagesNotVisible")));age.set(n(a.get("ApproximateAgeOfOldestMessage")));}catch(RuntimeException ex){log.debug("SQS queue metrics unavailable for role {}",name.equals(source)?"source":"dlq",ex);}}
    private static long n(String v){try{return Long.parseLong(v);}catch(Exception ignored){return 0;}}
    private static void gauge(MeterRegistry r,String queue,AtomicLong value,String type){r.gauge("fulfillx.sqs."+(type.equals("age")?"oldest_message.age":"messages."+type),Tags.of("service","order-service","queue",queue),value,AtomicLong::get);}
}

