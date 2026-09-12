package com.paytm.exercise.wallet.api;

import com.paytm.exercise.wallet.application.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> domain(ApiException exception, HttpServletRequest request) {
        return problem(exception.status(), exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> invalidRequest(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid_request", "Request fields are missing, malformed, or outside permitted limits.", request);
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ProblemDetail> database(DataAccessException exception, HttpServletRequest request) {
        log.warn("Database operation unavailable", exception);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "database_unavailable", "The request could not be completed; retry with the same idempotency key.", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unexpected request failure", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "An unexpected error occurred.", request);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setType(URI.create("https://wallet.example/problems/" + code));
        body.setInstance(URI.create(request.getRequestURI()));
        body.setProperty("code", code);
        body.setProperty("correlation_id", MDC.get("correlation_id"));
        return ResponseEntity.status(status).body(body);
    }
}

