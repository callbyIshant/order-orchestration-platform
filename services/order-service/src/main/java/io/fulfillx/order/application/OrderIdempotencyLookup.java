package io.fulfillx.order.application;

import io.fulfillx.order.api.dto.OrderAcceptedResponse;
import io.fulfillx.order.domain.OrderException;
import io.fulfillx.order.persistence.IdempotencyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class OrderIdempotencyLookup {
    private final IdempotencyRepository keys; private final OrderViewMapper views;
    public OrderIdempotencyLookup(IdempotencyRepository keys,OrderViewMapper views){this.keys=keys;this.views=views;}
    @Transactional(readOnly=true)
    public OrderAcceptedResponse replay(String customer,String key,String fingerprint){
        var existing=keys.findWithOrder(customer,key).orElse(null);
        if(existing==null||!existing.getExpiresAt().isAfter(Instant.now()))return null;
        if(!existing.getFingerprint().equals(fingerprint))throw new OrderException("IDEMPOTENCY_KEY_REUSED","Idempotency-Key is active for a different request.",409);
        return views.accepted(existing.getOrder());
    }
}
