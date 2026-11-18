package io.fulfillx.payment.persistence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Repository
public class PaymentOutboxStore {
 public record Row(UUID messageId,String payload,int attempts,UUID leaseToken){}
 private final JdbcTemplate jdbc; public PaymentOutboxStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void append(UUID messageId,String key,UUID paymentId,String type,String payload){jdbc.update("INSERT INTO payment_outbox(message_id,deduplication_key,aggregate_id,message_type,payload) VALUES (?,?,?,?,?) ON CONFLICT(deduplication_key) DO NOTHING",messageId,key,paymentId,type,payload);}
 @Transactional public List<Row> claim(int limit,int lease){UUID token=UUID.randomUUID();String sql="WITH c AS (SELECT message_id FROM payment_outbox WHERE published_at IS NULL AND next_attempt_at<=CURRENT_TIMESTAMP AND (lease_until IS NULL OR lease_until<=CURRENT_TIMESTAMP) ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT ?) UPDATE payment_outbox p SET lease_token=?,lease_until=CURRENT_TIMESTAMP+(?*INTERVAL '1 second'),attempts=attempts+1 FROM c WHERE p.message_id=c.message_id RETURNING p.message_id,p.payload,p.attempts,p.lease_token";return jdbc.query(sql,(r,n)->new Row(r.getObject(1,UUID.class),r.getString(2),r.getInt(3),r.getObject(4,UUID.class)),limit,token,lease);}
 @Transactional public void published(UUID id,UUID token){jdbc.update("UPDATE payment_outbox SET published_at=CURRENT_TIMESTAMP,lease_token=NULL,lease_until=NULL WHERE message_id=? AND lease_token=?",id,token);}
 @Transactional public void retry(UUID id,UUID token,int attempts,int base){int cap=Math.min(300,Math.max(1,base)*(1<<Math.min(8,Math.max(0,attempts-1))));int seconds=java.util.concurrent.ThreadLocalRandom.current().nextInt(Math.max(1,cap/2),cap+1);jdbc.update("UPDATE payment_outbox SET next_attempt_at=CURRENT_TIMESTAMP+(?*INTERVAL '1 second'),lease_token=NULL,lease_until=NULL WHERE message_id=? AND lease_token=?",seconds,id,token);}
}
