package com.finsecure.validator.requestlog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.finsecure.validator.config.AppProperties;
import com.finsecure.validator.transaction.Masking;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Captures every call to the public API and stores it in the api_request_log table.
 * Dashboard pages, static files and actuator endpoints are not recorded.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiRequestLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiRequestLogFilter.class);
    private static final List<String> LOGGED_PREFIXES = List.of("/validate", "/health", "/api/");

    private final ApiRequestLogRepository repository;
    private final AppProperties app;
    private final Clock clock;

    public ApiRequestLogFilter(ApiRequestLogRepository repository, AppProperties app, Clock clock) {
        this.repository = repository;
        this.app = app;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return LOGGED_PREFIXES.stream().noneMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        ContentCachingRequestWrapper wrappedRequest =
                new ContentCachingRequestWrapper(request, app.maxLoggedBodyLength());
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        wrappedResponse.setHeader(RequestLogAttributes.REQUEST_ID_HEADER, requestId);

        Instant start = Instant.now(clock);
        long startNanos = System.nanoTime();
        MDC.put("requestId", requestId);
        try {
            chain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            try {
                repository.save(buildEntry(requestId, start, durationMs, wrappedRequest, wrappedResponse));
            } catch (RuntimeException ex) {
                log.warn("Could not store request log {}: {}", requestId, ex.getMessage());
            }
            MDC.remove("requestId");
            wrappedResponse.copyBodyToResponse();
        }
    }

    private ApiRequestLog buildEntry(String requestId, Instant start, long durationMs,
                                     ContentCachingRequestWrapper request, ContentCachingResponseWrapper response) {
        ApiRequestLog entry = new ApiRequestLog();
        entry.setRequestId(requestId);
        entry.setCreatedAt(start);
        entry.setMethod(request.getMethod());
        entry.setPath(truncate(request.getRequestURI(), 255));
        entry.setQueryString(truncate(request.getQueryString(), 1000));
        entry.setStatusCode(response.getStatus());
        entry.setDurationMs(durationMs);
        entry.setClientIp(truncate(clientIp(request), 64));
        entry.setUserAgent(truncate(request.getHeader("User-Agent"), 255));
        entry.setPodName(truncate(app.podName(), 100));
        entry.setRequestBody(body(request.getContentAsByteArray()));
        entry.setResponseBody(body(response.getContentAsByteArray()));

        Object txnId = request.getAttribute(RequestLogAttributes.TRANSACTION_ID);
        entry.setTransactionId(txnId == null ? null : truncate(txnId.toString(), 40));

        Object outcome = request.getAttribute(RequestLogAttributes.OUTCOME);
        entry.setOutcome(outcome != null ? outcome.toString() : defaultOutcome(response.getStatus()));
        return entry;
    }

    private String body(byte[] content) {
        if (content == null || content.length == 0) {
            return null;
        }
        String text = Masking.maskJson(new String(content, StandardCharsets.UTF_8));
        return truncate(text, Math.min(app.maxLoggedBodyLength(), ApiRequestLog.BODY_COLUMN_LENGTH));
    }

    private static String defaultOutcome(int status) {
        if (status >= 500) {
            return "ERROR";
        }
        return status >= 400 ? "FAILED" : "OK";
    }

    private static String resolveRequestId(HttpServletRequest request) {
        String incoming = request.getHeader(RequestLogAttributes.REQUEST_ID_HEADER);
        if (incoming != null && incoming.matches("^[A-Za-z0-9-]{8,36}$")) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
