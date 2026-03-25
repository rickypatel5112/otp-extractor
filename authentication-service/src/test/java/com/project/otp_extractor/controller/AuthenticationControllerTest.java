package com.project.otp_extractor.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.otp_extractor.dtos.*;
import com.project.otp_extractor.exceptions.UserAlreadyExistsException;
import com.project.otp_extractor.exceptions.UserNotFoundException;
import com.project.otp_extractor.services.AuthenticationService;
import com.project.otp_extractor.services.JwtService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthenticationController.class)
@AutoConfigureMockMvc(addFilters = false)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@TestPropertySource(properties = {"AUTH_SERVICE_PORT=8080"})
class AuthenticationControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AuthenticationService authenticationService;

    @MockitoBean private JwtService jwtService;

    @Autowired private ObjectMapper objectMapper;

    @Test
    void shouldRegisterUserSuccessfully() throws Exception {
        RegisterRequest request =
                new RegisterRequest("firstName", "lastName", "test@gmail.com", "@1Password");

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().string("User registered successfully!"));

        Mockito.verify(authenticationService).register(any());
    }

    @Test
    void shouldReturn409WhenUserAlreadyExists() throws Exception {
        RegisterRequest request =
                new RegisterRequest("firstName", "lastName", "test@gmail.com", "@1Password");

        doThrow(new UserAlreadyExistsException("User already exists"))
                .when(authenticationService)
                .register(any());

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn400WhenRegisterRequestIsInvalid() throws Exception {
        RegisterRequest request = new RegisterRequest("", "", "not-an-email", "weak");

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAuthenticateAndSetCookie() throws Exception {
        AuthenticationRequest request = new AuthenticationRequest("user@gmail.com", "password");
        AuthenticationService.TokenPair tokenPair =
                new AuthenticationService.TokenPair("access-token", "refresh-token");

        when(authenticationService.authenticate(any())).thenReturn(tokenPair);

        mockMvc.perform(
                        post("/api/v1/auth/authenticate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void shouldReturn400WhenAuthenticateRequestIsInvalid() throws Exception {
        AuthenticationRequest request = new AuthenticationRequest("", "");

        mockMvc.perform(
                        post("/api/v1/auth/authenticate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRefreshTokenSuccessfully() throws Exception {
        AuthenticationService.TokenPair tokenPair =
                new AuthenticationService.TokenPair("new-access", "new-refresh");

        when(authenticationService.issueNewNonExpiredToken("refresh-token")).thenReturn(tokenPair);

        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "refreshToken", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.accessToken").value("new-access"));
    }

    @Test
    void shouldReturn400WhenRefreshTokenCookieIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn401WhenRefreshTokenIsExpiredOrInvalid() throws Exception {
        when(authenticationService.issueNewNonExpiredToken(any()))
                .thenThrow(new io.jsonwebtoken.JwtException("expired"));

        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "refreshToken", "expired-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldLogoutSuccessfullyAndClearCookie() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "refreshToken", "refresh-token"))
                                .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        "Set-Cookie",
                                        org.hamcrest.Matchers.containsString("Max-Age=0")))
                .andExpect(content().string("Logged out successfully!"));

        Mockito.verify(authenticationService).logout("Bearer access-token", "refresh-token");
    }

    @Test
    void shouldReturn400WhenLogoutRefreshTokenCookieIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer access-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenLogoutAuthorizationHeaderIsMissing() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "refreshToken", "refresh-token")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnOkForForgotPasswordAlways() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("user@gmail.com", "frontend-url");

        mockMvc.perform(
                        post("/api/v1/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "If a user with that email exists")));
    }

    @Test
    void shouldReturn500WhenAmqpFails() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("user@gmail.com", "frontend-url");

        doThrow(new org.springframework.amqp.AmqpException("fail"))
                .when(authenticationService)
                .forgotPassword(any());

        mockMvc.perform(
                        post("/api/v1/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void shouldResetPasswordSuccessfully() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("@1newPassword");

        when(authenticationService.resetPassword(any(), any())).thenReturn(true);

        mockMvc.perform(
                        post("/api/v1/auth/reset-password")
                                .param("token", "valid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("Password reset successful"));
    }

    @Test
    void shouldReturnBadRequestWhenResetFails() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("@1newPassword");

        when(authenticationService.resetPassword(any(), any())).thenReturn(false);

        mockMvc.perform(
                        post("/api/v1/auth/reset-password")
                                .param("token", "valid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnUnauthorizedWhenJwtExceptionThrown() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("@1newPassword");

        when(authenticationService.resetPassword(any(), any()))
                .thenThrow(new io.jsonwebtoken.JwtException("invalid"));

        mockMvc.perform(
                        post("/api/v1/auth/reset-password")
                                .param("token", "bad-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn500WhenUnexpectedExceptionThrownDuringReset() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("@1newPassword");

        when(authenticationService.resetPassword(any(), any()))
                .thenThrow(new RuntimeException("unexpected"));

        mockMvc.perform(
                        post("/api/v1/auth/reset-password")
                                .param("token", "valid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void shouldReturn400WhenResetPasswordTokenParamIsMissing() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("@1newPassword");

        mockMvc.perform(
                        post("/api/v1/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldDeleteAccountSuccessfully() throws Exception {
        DeleteAccountRequest request = new DeleteAccountRequest("user@gmail.com");

        mockMvc.perform(
                        delete("/api/v1/auth/delete-account")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("Account deleted successfully!"));

        Mockito.verify(authenticationService).deleteAccount("user@gmail.com");
    }

    @Test
    void shouldReturnNotFoundWhenUserDoesNotExist() throws Exception {
        DeleteAccountRequest request = new DeleteAccountRequest("user@gmail.com");

        doThrow(new UserNotFoundException("No user found with email: user@gmail.com"))
                .when(authenticationService)
                .deleteAccount("user@gmail.com");

        mockMvc.perform(
                        delete("/api/v1/auth/delete-account")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
}
