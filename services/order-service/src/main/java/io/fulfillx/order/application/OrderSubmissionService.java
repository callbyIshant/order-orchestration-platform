package io.fulfillx.order.application;

import io.fulfillx.order.api.dto.CreateOrderRequest;
import io.fulfillx.order.catalog.CatalogClientException;
import io.fulfillx.order.catalog.CatalogProduct;
import io.fulfillx.order.catalog.CatalogProductClient;
import io.fulfillx.order.catalog.CatalogProperties;
import io.fulfillx.order.domain.OrderException;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@Service
public class OrderSubmissionService {
    private final CatalogProductClient catalog; private final java.util.concurrent.Executor executor; private final OrderCreationPersistenceService persistence; private final OrderViewMapper views; private final OrderIdempotencyLookup idempotency; private final CatalogProperties catalogProperties;
    public OrderSubmissionService(CatalogProductClient catalog,@org.springframework.beans.factory.annotation.Qualifier("catalogLookupExecutor") java.util.concurrent.Executor executor,OrderCreationPersistenceService persistence,OrderViewMapper views,OrderIdempotencyLookup idempotency,CatalogProperties catalogProperties){this.catalog=catalog;this.executor=executor;this.persistence=persistence;this.views=views;this.idempotency=idempotency;this.catalogProperties=catalogProperties;}
    public OrderSubmission submit(String customer,String idempotencyKey,CreateOrderRequest request) {
        if(idempotencyKey==null || !idempotencyKey.matches("[A-Za-z0-9._:-]{1,128}")) throw new OrderException("INVALID_IDEMPOTENCY_KEY","Idempotency-Key is required and must be 1 to 128 safe characters.",400);
        TreeMap<UUID,Integer> quantities=new TreeMap<>();
        try { for(var item:request.items()) quantities.merge(item.productId(),item.quantity(),Math::addExact); }
        catch(ArithmeticException ex){throw new OrderException("INVALID_QUANTITY","Combined product quantity is too large.",400);}
        String fingerprint=fingerprint(quantities,request.paymentToken());
        var replay=idempotency.replay(customer,idempotencyKey,fingerprint);
        if(replay!=null)return new OrderSubmission(replay,true);
        var futures=new LinkedHashMap<UUID,CompletableFuture<CatalogProduct>>();
        try { quantities.keySet().forEach(id->futures.put(id,CompletableFuture.supplyAsync(()->catalog.getOrderableProduct(id),executor))); }
        catch(RejectedExecutionException ex){futures.values().forEach(f->f.cancel(true));throw new OrderException("CATALOG_UNAVAILABLE","Catalog lookup capacity is full; retry the request.",503);}
        try { CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new)).get(catalogProperties.totalTimeout().toMillis(),TimeUnit.MILLISECONDS); }
        catch(TimeoutException|InterruptedException ex){futures.values().forEach(f->f.cancel(true));if(ex instanceof InterruptedException)Thread.currentThread().interrupt();throw new OrderException("CATALOG_UNAVAILABLE","Catalog validation timed out; retry the request.",503);}
        catch(ExecutionException ex){Throwable cause=ex.getCause();if(cause instanceof CatalogClientException ce && ce.getKind()==CatalogClientException.Kind.NOT_ORDERABLE)throw new OrderException("PRODUCT_NOT_ORDERABLE",ce.getMessage(),422);throw new OrderException("CATALOG_UNAVAILABLE","Catalog is temporarily unavailable; retry the request.",503);}
        List<OrderCreationPersistenceService.Item> snapshots=quantities.entrySet().stream().map(e->{var p=futures.get(e.getKey()).join();return new OrderCreationPersistenceService.Item(p.id(),p.name(),p.unitPrice(),e.getValue());}).toList();
        var result=persistence.create(customer,idempotencyKey,fingerprint,request.paymentToken(),snapshots);
        return new OrderSubmission(views.accepted(result.order()),result.replay());
    }
    private static String fingerprint(SortedMap<UUID,Integer> quantities,String token){try{var digest=MessageDigest.getInstance("SHA-256");String canonical=quantities.entrySet().stream().map(e->e.getKey()+":"+e.getValue()).reduce((a,b)->a+"|"+b).orElse("")+"|"+token;return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
    public record OrderSubmission(io.fulfillx.order.api.dto.OrderAcceptedResponse response,boolean replay){}
}
