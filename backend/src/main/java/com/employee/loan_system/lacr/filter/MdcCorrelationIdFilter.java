package com.employee.loan_system.lacr.filter;

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

/**
 * Filter that injects a correlation ID into the MDC (Mapped Diagnostic Context)
 * for every incoming HTTP request, so every log line in that request thread carries it.
 *
 * Interview answer:
 * "In a distributed system, debugging a failure means correlating log lines across
 *  multiple service calls. We use a X-Correlation-Id header (set by the API gateway
 *  or the client) and propagate it via MDC. Every log line emitted during that
 *  request — including Kafka consumers that pull the correlationId from the event header
 *  — carries the same ID. This makes tracing a single closure request end-to-end trivial."
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcCorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String MDC_CORRELATION_KEY = "correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_CORRELATION_KEY, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId); // echo back to caller

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Always clear MDC to prevent thread-local leaks in thread pool scenarios
            MDC.remove(MDC_CORRELATION_KEY);
        }
    }
}
