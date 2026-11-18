package io.fulfillx.inventory.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InventoryCorrelationIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String id;
        try { id = request.getHeader("X-Correlation-ID") == null ? UUID.randomUUID().toString() : UUID.fromString(request.getHeader("X-Correlation-ID")).toString(); }
        catch (IllegalArgumentException ex) { id = UUID.randomUUID().toString(); }
        request.setAttribute(InventoryApiErrors.CORRELATION_ID, id);
        response.setHeader("X-Correlation-ID", id);
        String previous = MDC.get("correlationId");
        MDC.put("correlationId", id);
        try { chain.doFilter(request, response); }
        finally { if (previous == null) MDC.remove("correlationId"); else MDC.put("correlationId", previous); }
    }
}
