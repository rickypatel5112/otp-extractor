package com.project.otp_extractor.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticationRequest {

    @Email(message = "Invalid email address")
    @NotBlank(message = "Email is required")
    @NotNull
    private String email;

    @NotBlank(message = "Password is required")
    @NotNull
    private String password;
}
