package com.project.otp_extractor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.otp_extractor.dtos.*;
import com.project.otp_extractor.services.JwtService;
import com.project.otp_extractor.user.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class AuthenticationControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private UserRepository userRepository;

    private String email;
    private final String password = "Password123!";

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:alpine").withExposedPorts(6379);

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:alpine");

    @Container
    static RabbitMQContainer rabbitmq =
            new RabbitMQContainer("rabbitmq:4.2.0-management-alpine").withExposedPorts(5672);

    private JwtService jwtService;

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);

        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getFirstMappedPort);
    }

    @BeforeEach
    void setUp() throws Exception {
        email = "user-" + UUID.randomUUID() + "@example.com";

        RegisterRequest request =
                RegisterRequest.builder()
                        .firstname("Test")
                        .lastname("User")
                        .email(email)
                        .password(password)
                        .build();

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private AuthTokens authenticate() throws Exception {
        AuthenticationRequest authRequest = new AuthenticationRequest(email, password);

        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(authRequest)))
                        .andExpect(status().isOk())
                        .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());

        return new AuthTokens(
                json.get("accessToken").asText(), result.getResponse().getCookie("refreshToken"));
    }

    private record AuthTokens(String accessToken, Cookie refreshTokenCookie) {}

    @Test
    void shouldAuthenticateUserAndReturnTokens() throws Exception {
        AuthTokens tokens = authenticate();
        String accessToken = tokens.accessToken();
        String refreshToken = tokens.refreshTokenCookie().getValue();

        assertNotNull(accessToken);
        assertNotNull(refreshToken);
    }

    @Test
    void shouldRefreshTokenSuccessfully() throws Exception {
        AuthTokens tokens = authenticate();

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(tokens.refreshTokenCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void shouldLogoutSuccessfully() throws Exception {
        AuthTokens tokens = authenticate();

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .header("Authorization", "Bearer " + tokens.accessToken())
                                .cookie(tokens.refreshTokenCookie()))
                .andExpect(status().isOk())
                .andExpect(content().string("Logged out successfully!"));
    }

    @Test
    void shouldDeleteAccountSuccessfully() throws Exception {
        AuthTokens tokens = authenticate();

        mockMvc.perform(
                        delete("/api/v1/auth/delete-account/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + email + "\"}")
                                .header("Authorization", "Bearer " + tokens.accessToken())
                                .cookie(tokens.refreshTokenCookie()))
                .andExpect(status().isOk())
                .andExpect(content().string("Account deleted successfully!"));

        assertTrue(userRepository.findByEmail(email).isEmpty());
    }

    @Test
    void shouldFailWhenEmailIsInvalid() throws Exception {
        RegisterRequest request =
                RegisterRequest.builder()
                        .firstname("Test")
                        .lastname("User")
                        .email("invalid-email")
                        .password("Password123!")
                        .build();

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldFailWhenPasswordIsWeak() throws Exception {
        RegisterRequest request =
                RegisterRequest.builder()
                        .firstname("Test")
                        .lastname("User")
                        .email("weak.pass@example.com")
                        .password("123") // weak
                        .build();

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldFailWhenFirstnameIsBlank() throws Exception {
        RegisterRequest request =
                RegisterRequest.builder()
                        .firstname("")
                        .lastname("User")
                        .email("test1@example.com")
                        .password("Password123!")
                        .build();

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldFailWhenLastnameIsBlank() throws Exception {
        RegisterRequest request =
                RegisterRequest.builder()
                        .firstname("Test")
                        .lastname("")
                        .email("test2@example.com")
                        .password("Password123!")
                        .build();

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldFailWhenEmailAlreadyExists() throws Exception {
        // user already created in @BeforeEach
        RegisterRequest request =
                RegisterRequest.builder()
                        .firstname("Test")
                        .lastname("User")
                        .email(email)
                        .password(password)
                        .build();

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldFailAuthenticationWithWrongPassword() throws Exception {
        AuthenticationRequest request = new AuthenticationRequest(email, "WrongPassword");

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldFailAuthenticationWithNonExistentUser() throws Exception {
        AuthenticationRequest request =
                new AuthenticationRequest("nouser@example.com", "Password123!");

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldFailRefreshWithoutCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isBadRequest());
    }

    @Test
    void shouldFailRefreshWithInvalidToken() throws Exception {
        Cookie invalidCookie = new Cookie("refreshToken", "invalid-token");

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(invalidCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldFailLogoutWithoutAccessToken() throws Exception {
        AuthTokens tokens = authenticate();

        mockMvc.perform(post("/api/v1/auth/logout").cookie(tokens.refreshTokenCookie()))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldFailLogoutWithoutRefreshToken() throws Exception {
        AuthTokens tokens = authenticate();

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldFailDeleteWithoutAuth() throws Exception {
        mockMvc.perform(
                        delete("/api/v1/auth/delete-account/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldFailDeleteWithInvalidToken() throws Exception {
        mockMvc.perform(
                        delete("/api/v1/auth/delete-account/me")
                                .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldDeleteOnlyAuthenticatedUsersAccount() throws Exception {
        // User A
        AuthTokens userATokens = authenticate();

        // User B
        String otherEmail = "other-" + UUID.randomUUID() + "@example.com";

        RegisterRequest otherUser =
                RegisterRequest.builder()
                        .firstname("Other")
                        .lastname("User")
                        .email(otherEmail)
                        .password(password)
                        .build();

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(otherUser)))
                .andExpect(status().isCreated());

        // Delete using User A's token
        mockMvc.perform(
                        delete("/api/v1/auth/delete-account/me")
                                .header("Authorization", "Bearer " + userATokens.accessToken())
                                .cookie(userATokens.refreshTokenCookie()))
                .andExpect(status().isOk());

        // Verify User B still exists (not deleted)
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {
                            "email": "%s",
                            "password": "%s"
                        }
                        """
                                                .formatted(otherEmail, password)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldNotAllowRequestsAfterLogout() throws Exception {
        AuthTokens tokens = authenticate();

        // Logout
        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .header("Authorization", "Bearer " + tokens.accessToken())
                                .cookie(tokens.refreshTokenCookie()))
                .andExpect(status().isOk());

        // Try using same token again
        mockMvc.perform(
                        delete("/api/v1/auth/delete-account/me")
                                .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldFailWithMalformedAuthorizationHeader() throws Exception {
        mockMvc.perform(
                        delete("/api/v1/auth/delete-account/me")
                                .header("Authorization", "InvalidHeader"))
                .andExpect(status().isForbidden());
    }
}
