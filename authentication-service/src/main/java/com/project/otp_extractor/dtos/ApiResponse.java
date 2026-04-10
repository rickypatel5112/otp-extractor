package com.project.otp_extractor.dtos;

import java.time.Instant;

public record ApiResponse<T>(int status, String message, T data, String timestamp) {
    public static <T> ApiResponse<T> success(int status, String message, T data) {
        return new ApiResponse<>(status, message, data, Instant.now().toString());
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, message, data, Instant.now().toString());
    }

    public static ApiResponse<Void> success(String message) {
        return new ApiResponse<>(200, message, null, Instant.now().toString());
    }

    public static <T> ApiResponse<T> error(int status, String message, T data) {
        return new ApiResponse<>(status, message, data, Instant.now().toString());
    }
}
