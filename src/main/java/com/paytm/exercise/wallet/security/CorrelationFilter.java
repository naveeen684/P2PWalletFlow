package com.paytm.exercise.wallet.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationFilter extends OncePerRequestFilter {
    private static final int MAX_CORRELATION_ID_LENGTH = 128;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = valid(request.getHeader("X-Correlation-ID")) ? request.getHeader("X-Correlation-ID") : UUID.randomUUID().toString();
        String traceId = traceId(request.getHeader("traceparent"));
        MDC.put("correlation_id", correlationId);
        MDC.put("trace_id", traceId);
        response.setHeader("X-Correlation-ID", correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("correlation_id");
            MDC.remove("trace_id");
        }
    }

    private boolean valid(String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_CORRELATION_ID_LENGTH && value.chars().allMatch(c -> c >= 33 && c <= 126);
    }

    private String traceId(String traceparent) {
        if (traceparent != null) {
            String[] values = traceparent.split("-");
            if (values.length == 4 && values[1].matches("[0-9a-fA-F]{32}")) {
                return values[1].toLowerCase();
            }
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}

