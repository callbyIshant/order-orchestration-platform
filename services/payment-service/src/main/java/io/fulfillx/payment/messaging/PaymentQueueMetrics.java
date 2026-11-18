package io.fulfillx.payment.messaging;

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

@Component public class PaymentQueueMetrics {
    private static final Logger log=LoggerFactory.getLogger(PaymentQueueMetrics.class);
    private final SqsClient sqs;private final String source,dlq;
    private final AtomicLong sourceVisible=new AtomicLong(),sourceInflight=new AtomicLong(),sourceAge=new AtomicLong(),dlqVisible=new AtomicLong(),dlqInflight=new AtomicLong(),dlqAge=new AtomicLong();
    public PaymentQueueMetrics(SqsClient sqs,MeterRegistry meters,@Value("${payment.messaging.queue-name}")String source,@Value("${payment.messaging.dlq-name}")String dlq){this.sqs=sqs;this.source=source;this.dlq=dlq;gauge(meters,"payment-commands",sourceVisible);inflightGauge(meters,"payment-commands",sourceInflight);ageGauge(meters,"payment-commands",sourceAge);gauge(meters,"payment-commands-dlq",dlqVisible);inflightGauge(meters,"payment-commands-dlq",dlqInflight);ageGauge(meters,"payment-commands-dlq",dlqAge);}
    @Scheduled(fixedDelayString="${fulfillx.queue-metrics.poll-ms:30000}",initialDelayString="${fulfillx.queue-metrics.initial-delay-ms:10000}") public void poll(){poll(source,sourceVisible,sourceInflight,sourceAge);poll(dlq,dlqVisible,dlqInflight,dlqAge);}
    private void poll(String name,AtomicLong visible,AtomicLong inflight,AtomicLong age){try{String url=sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build()).queueUrl();var a=sqs.getQueueAttributes(GetQueueAttributesRequest.builder().queueUrl(url).attributeNamesWithStrings("ApproximateNumberOfMessages","ApproximateNumberOfMessagesNotVisible","ApproximateAgeOfOldestMessage").build()).attributesAsStrings();visible.set(n(a.get("ApproximateNumberOfMessages")));inflight.set(n(a.get("ApproximateNumberOfMessagesNotVisible")));age.set(n(a.get("ApproximateAgeOfOldestMessage")));}catch(RuntimeException ex){log.debug("SQS queue metrics unavailable for role {}",name.equals(source)?"source":"dlq",ex);}}
    private static long n(String v){try{return Long.parseLong(v);}catch(Exception ignored){return 0;}}
    private static void gauge(MeterRegistry r,String queue,AtomicLong value){r.gauge("fulfillx.sqs.messages.visible",Tags.of("service","payment-service","queue",queue),value,AtomicLong::get);}
    private static void inflightGauge(MeterRegistry r,String queue,AtomicLong value){r.gauge("fulfillx.sqs.messages.inflight",Tags.of("service","payment-service","queue",queue),value,AtomicLong::get);}
    private static void ageGauge(MeterRegistry r,String queue,AtomicLong value){r.gauge("fulfillx.sqs.oldest_message.age",Tags.of("service","payment-service","queue",queue),value,AtomicLong::get);}
}



