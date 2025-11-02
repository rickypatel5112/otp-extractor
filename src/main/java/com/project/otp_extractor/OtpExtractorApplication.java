package com.project.otp_extractor;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@SpringBootApplication
@EnableCaching
public class OtpExtractorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OtpExtractorApplication.class, args);
    }

//    @Bean
//    CommandLineRunner runRequests() {
//        return args -> {
//            RestTemplate restTemplate = new RestTemplate();
//
//            String registerUrl = "http://localhost:8080/api/v1/auth/register";
//            String loginUrl = "http://localhost:8080/api/v1/auth/authenticate";
//            String homeUrl = "http://localhost:8080/home";
//            String logoutUrl = "http://localhost:8080/api/v1/auth/logout";
//
//            HttpHeaders headers = new HttpHeaders();
//            headers.setContentType(MediaType.APPLICATION_JSON);
//
//            // Register request
//            Map<String, String> registerBody = Map.of(
//                    "firstname", "AA",
//                    "lastname", "AA",
//                    "email", "a@a.cfo",
//                    "password", "12341234"
//            );
//            try {
//                restTemplate.postForEntity(registerUrl, new HttpEntity<>(registerBody, headers), String.class);
//            } catch (Exception e) {
//                System.out.println("Register likely failed (user exists): " + e.getMessage());
//            }
//
//            // Authenticate request
//            Map<String, String> loginBody = Map.of(
//                    "email", "a@a.cfo",
//                    "password", "12341234"
//            );
//
//            ResponseEntity<Map> loginResponse = restTemplate.exchange(
//                    loginUrl,
//                    HttpMethod.POST,
//                    new HttpEntity<>(loginBody, headers),
//                    Map.class
//            );
//
//            // Extract access token from JSON body
//            String accessToken = (String) loginResponse.getBody().get("accessToken");
//            System.out.println("Access Token: " + accessToken);
//
//            // Extract refresh token from response cookie
//            List<String> setCookieHeaders = loginResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
//            String refreshToken = null;
//            if (setCookieHeaders != null) {
//                for (String cookie : setCookieHeaders) {
//                    if (cookie.startsWith("refreshToken=")) {
//                        refreshToken = cookie.split("refreshToken=")[1].split(";")[0];
//                        break;
//                    }
//                }
//            }
//            System.out.println("Refresh Token (from cookie): " + refreshToken);
//
//            HttpHeaders homeHeaders = new HttpHeaders();
//            homeHeaders.setBearerAuth(accessToken);
//
//            // Create the request entity (no body needed for GET)
//            HttpEntity<Void> entity = new HttpEntity<>(homeHeaders);
//
//            // Make the GET request
//            ResponseEntity<Map> response = restTemplate.exchange(
//                    homeUrl,
//                    HttpMethod.GET,
//                    entity,
//                    Map.class
//            );
//
//            // Print response
//            if (response.getStatusCode() == HttpStatus.OK) {
//                System.out.println("Home request successful (200 OK)");
//            } else {
//                System.out.println("Unexpected status: " + response.getStatusCode());
//            }
//            System.out.println("Body: " + response.getBody());
//
//            if (accessToken != null && refreshToken != null) {
//                // Send logout request
//                HttpHeaders logoutHeaders = new HttpHeaders();
//                logoutHeaders.set(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
//                logoutHeaders.add(HttpHeaders.COOKIE, "refreshToken=" + refreshToken);
//
//                HttpEntity<Void> logoutRequest = new HttpEntity<>(logoutHeaders);
//
//                ResponseEntity<String> logoutResponse = restTemplate.exchange(
//                        logoutUrl,
//                        HttpMethod.POST,
//                        logoutRequest,
//                        String.class
//                );
//
//                System.out.println("Logout response: " + logoutResponse.getBody());
//            } else {
//                System.out.println("Tokens missing — skipping logout.");
//            }
//        };
//    }
}
