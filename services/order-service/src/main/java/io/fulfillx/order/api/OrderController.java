package io.fulfillx.order.api;

import io.fulfillx.order.api.dto.CreateOrderRequest;
import io.fulfillx.order.application.OrderReadService;
import io.fulfillx.order.application.OrderSubmissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderSubmissionService submission; private final OrderReadService reads;
    public OrderController(OrderSubmissionService submission,OrderReadService reads){this.submission=submission;this.reads=reads;}
    @PostMapping
    public ResponseEntity<?> create(@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody CreateOrderRequest request,@AuthenticationPrincipal Jwt jwt){
        var result=submission.submit(jwt.getSubject(),key,request);
        return ResponseEntity.accepted().location(URI.create(result.response().statusUrl())).header("Idempotency-Replayed",Boolean.toString(result.replay())).body(result.response());
    }
    @GetMapping("/{orderId}") public ResponseEntity<?> get(@PathVariable UUID orderId,@AuthenticationPrincipal Jwt jwt){return ResponseEntity.ok(reads.get(orderId,jwt.getSubject()));}
    @GetMapping("/{orderId}/history") public ResponseEntity<?> history(@PathVariable UUID orderId,@AuthenticationPrincipal Jwt jwt){return ResponseEntity.ok(reads.history(orderId,jwt.getSubject()));}
}
