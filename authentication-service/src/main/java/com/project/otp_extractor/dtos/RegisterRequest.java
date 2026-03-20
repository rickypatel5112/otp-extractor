package com.project.otp_extractor.dtos;

import com.project.otp_extractor.annotation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 100)
    private String firstname;

    @NotBlank(message = "Last name is required")
    @Size(max = 100)
    private String lastname;

    @Email(message = "Invalid email address")
    @NotBlank(message = "Email is required")
    private String email;

    @ValidPassword private String password;
}
