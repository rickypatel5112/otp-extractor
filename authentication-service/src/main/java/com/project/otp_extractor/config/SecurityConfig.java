package com.project.otp_extractor.config;

import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.passay.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String AUTH_BASE = "/api/v1/auth";
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordValidator passwordValidator() {
        return new PasswordValidator(
                Arrays.asList(
                        new LengthRule(8, 254),
                        new CharacterRule(EnglishCharacterData.UpperCase, 1),
                        new CharacterRule(EnglishCharacterData.LowerCase, 1),
                        new CharacterRule(EnglishCharacterData.Digit, 1),
                        new CharacterRule(EnglishCharacterData.Special, 1),
                        new WhitespaceRule()));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, CorsConfigurationSource corsConfigurationSource) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(
                        request -> {
                            request.requestMatchers(HttpMethod.OPTIONS, "/**")
                                    .permitAll()
                                    .requestMatchers(
                                            AUTH_BASE + "/register",
                                            AUTH_BASE + "/login",
                                            AUTH_BASE + "/refresh",
                                            AUTH_BASE + "/forgot-password",
                                            AUTH_BASE + "/reset-password",
                                            "/try")
                                    .permitAll()
                                    .anyRequest()
                                    .authenticated();
                        })
                .sessionManagement(
                        session -> {
                            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS);
                        })
                .addFilterBefore(
                        jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(
                List.of(
                        "https://localhost:5173", "https://localhost:5174",
                        "http://localhost:5173", "http://localhost:5174"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
