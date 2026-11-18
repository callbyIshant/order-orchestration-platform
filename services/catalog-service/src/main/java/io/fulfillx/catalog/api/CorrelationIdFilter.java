package io.fulfillx.catalog.api;

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
public class CorrelationIdFilter extends OncePerRequestFilter {
    private static final String HEADER = "X-Correlation-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requested = request.getHeader(HEADER);
        String id;
        try { id = requested == null ? UUID.randomUUID().toString() : UUID.fromString(requested).toString(); }
        catch (IllegalArgumentException ignored) { id = UUID.randomUUID().toString(); }
        request.setAttribute(ApiErrors.CORRELATION_ID, id);
        response.setHeader(HEADER, id);
        String previous = MDC.get("correlationId");
        MDC.put("correlationId", id);
        try { chain.doFilter(request, response); }
        finally { if (previous == null) MDC.remove("correlationId"); else MDC.put("correlationId", previous); }
    }
}
