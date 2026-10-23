package com.suntravels.config;

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
public class RequestCorrelationFilter extends OncePerRequestFilter
{
    private static final String REQUEST_ID = "requestId";
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        String previous = MDC.get(REQUEST_ID);
        // Generate locally: client headers cannot inject log text or impersonate another request.
        String id = UUID.randomUUID().toString();
        MDC.put(REQUEST_ID, id);
        response.setHeader("X-Request-ID", id);
        try { chain.doFilter(request, response); }
        finally {
            if (previous == null) MDC.remove(REQUEST_ID);
            else MDC.put(REQUEST_ID, previous);
        }
    }
}
