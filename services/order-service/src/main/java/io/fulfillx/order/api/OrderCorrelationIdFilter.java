package io.fulfillx.order.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OrderCorrelationIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader("X-Correlation-ID");
        String correlationId;
        try { correlationId = incoming == null ? UUID.randomUUID().toString() : UUID.fromString(incoming).toString(); }
        catch (IllegalArgumentException exception) { correlationId = UUID.randomUUID().toString(); }
        request.setAttribute("fulfillx.correlationId", correlationId);
        response.setHeader("X-Correlation-ID", correlationId);
        String prior = MDC.get("correlationId");
        MDC.put("correlationId", correlationId);
        try { chain.doFilter(request, response); }
        finally { if (prior == null) MDC.remove("correlationId"); else MDC.put("correlationId", prior); }
    }
}
