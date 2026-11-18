package io.fulfillx.notification.delivery;
import io.fulfillx.notification.domain.NotificationMessage;import jakarta.mail.internet.MimeMessage;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.mail.javamail.JavaMailSender;import org.springframework.mail.javamail.MimeMessageHelper;import org.springframework.stereotype.Component;import io.fulfillx.notification.messaging.NotificationProperties;
@Component @ConditionalOnProperty(prefix="notification.delivery",name="mode",havingValue="mailpit",matchIfMissing=true)
public class MailpitDeliveryAdapter implements NotificationDeliveryAdapter{
 private final JavaMailSender sender;private final NotificationProperties settings;public MailpitDeliveryAdapter(JavaMailSender sender,NotificationProperties settings){this.sender=sender;this.settings=settings;}
 @Override public void deliver(NotificationMessage n){try{MimeMessage m=sender.createMimeMessage();MimeMessageHelper h=new MimeMessageHelper(m,"UTF-8");h.setFrom(settings.delivery().from());h.setTo(n.recipient());h.setSubject(n.subject());h.setText(n.body(),false);m.setHeader("Message-ID","<"+n.sourceMessageId()+"@fulfillx.local>");m.setHeader("X-Correlation-ID",n.correlationId().toString());sender.send(m);}catch(Exception ex){throw new IllegalStateException("Mailpit delivery failed",ex);}}
}
