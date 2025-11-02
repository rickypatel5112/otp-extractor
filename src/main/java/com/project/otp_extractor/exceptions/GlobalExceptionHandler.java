package com.project.otp_extractor.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String error, Object message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", status.value());
        response.put("error", error);
        response.put("messages", message);
        response.put("timestamp", Instant.now().toString());
        return new ResponseEntity<>(response, status);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });

        return buildResponse(HttpStatus.BAD_REQUEST, "Validation Error", errors);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "User Already Exists", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", ex.getMessage());
    }
}
