package io.fulfillx.payment.persistence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.UUID;
@Repository
public class PaymentStore {
 public record Payment(UUID id,UUID orderId,BigDecimal amount,String currency,String status,String decision){}
 private final JdbcTemplate jdbc; public PaymentStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public boolean accept(UUID messageId,String type){return jdbc.update("INSERT INTO processed_messages(message_id,message_type) VALUES (?,?) ON CONFLICT DO NOTHING",messageId,type)==1;}
 public Payment byOrderForUpdate(UUID order){return jdbc.query("SELECT payment_id,order_id,amount,currency,status,decision_code FROM payments WHERE order_id=? FOR UPDATE",rs->rs.next()?map(rs):null,order);}
 public Payment byIdForUpdate(UUID id){return jdbc.query("SELECT payment_id,order_id,amount,currency,status,decision_code FROM payments WHERE payment_id=? FOR UPDATE",rs->rs.next()?map(rs):null,id);}
 public void insert(Payment p){jdbc.update("INSERT INTO payments(payment_id,order_id,amount,currency,status,decision_code) VALUES (?,?,?,?,?,?)",p.id(),p.orderId(),p.amount(),p.currency(),p.status(),p.decision());}
 public void updateStatus(UUID id,String status,String decision){jdbc.update("UPDATE payments SET status=?,decision_code=?,updated_at=CURRENT_TIMESTAMP WHERE payment_id=?",status,decision,id);}
 private Payment map(ResultSet rs)throws java.sql.SQLException{return new Payment(rs.getObject("payment_id",UUID.class),rs.getObject("order_id",UUID.class),rs.getBigDecimal("amount"),rs.getString("currency"),rs.getString("status"),rs.getString("decision_code"));}
}
