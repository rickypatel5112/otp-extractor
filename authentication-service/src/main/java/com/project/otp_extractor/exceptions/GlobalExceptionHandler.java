package com.project.otp_extractor.exceptions;

import io.jsonwebtoken.JwtException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<Map<String, Object>> buildResponse(
            HttpStatus status, String error, Object message) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status.value());
        body.put("error", error);
        body.put("messages", message);
        body.put("timestamp", Instant.now().toString());
        return new ResponseEntity<>(body, status);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, List<String>> fieldErrors = new HashMap<>();

        ex.getBindingResult()
                .getAllErrors()
                .forEach(
                        error -> {
                            String fieldName =
                                    (error instanceof FieldError fieldError)
                                            ? fieldError.getField()
                                            : error.getObjectName();

                            fieldErrors
                                    .computeIfAbsent(fieldName, key -> new ArrayList<>())
                                    .add(error.getDefaultMessage());
                        });

        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors);
    }

    @ExceptionHandler(MissingRequestCookieException.class)
    public ResponseEntity<Map<String, Object>> handleMissingCookie(
            MissingRequestCookieException ex) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Missing Cookie",
                "Required cookie '" + ex.getCookieName() + "' is missing");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingHeader(
            MissingRequestHeaderException ex) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Missing Header",
                "Required header '" + ex.getHeaderName() + "' is missing");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(
            MissingServletRequestParameterException ex) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Missing Parameter",
                "Required parameter '" + ex.getParameterName() + "' is missing");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials() {
        return buildResponse(
                HttpStatus.UNAUTHORIZED, "Authentication failed", "Invalid email or password");
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleUserAlreadyExists(
            UserAlreadyExistsException ex) {
        return buildResponse(HttpStatus.CONFLICT, "User Already Exists", ex.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(UserNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "User Not Found", ex.getMessage());
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<Map<String, Object>> handleJwtException(JwtException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid or expired token");
    }

    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<Map<String, Object>> handleAmqpException(AmqpException ex) {
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Messaging Error",
                "Internal messaging error. Please try again later.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred");
    }
}
