package com.finsecure.validator.common;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.finsecure.validator.requestlog.RequestLogAttributes;
import com.finsecure.validator.transaction.TransactionController;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice(assignableTypes = TransactionController.class)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is missing or is not valid JSON");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> mediaType(HttpServletRequest request) {
        return error(request, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Content-Type must be application/json");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error while processing {}", request.getRequestURI(), ex);
        return error(request, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error, please retry later");
    }

    private ResponseEntity<Map<String, Object>> error(HttpServletRequest request, HttpStatus status,
                                                      String code, String message) {
        request.setAttribute(RequestLogAttributes.OUTCOME, "ERROR");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ERROR");
        body.put("code", code);
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
