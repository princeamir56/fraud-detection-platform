package com.frauddetect.customer.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frauddetect.common.error.GlobalExceptionHandler;
import com.frauddetect.common.security.JwtAuthenticationFilter;
import com.frauddetect.common.security.JwtProperties;
import com.frauddetect.common.security.JwtService;
import com.frauddetect.customer.config.SecurityConfig;
import com.frauddetect.customer.dto.AuthResponse;
import com.frauddetect.customer.dto.LoginRequest;
import com.frauddetect.customer.dto.RegisterRequest;
import com.frauddetect.customer.service.AuthService;
import com.frauddetect.customer.service.InvalidCredentialsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-slice test for the public auth endpoints. Verifies that register/login are reachable without a
 * token (permitAll), that Bean Validation rejects malformed input with 400, and that a failed login
 * surfaces as 401 via {@link AuthExceptionHandler} (not the 500 catch-all).
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AuthExceptionHandler.class,
        AuthControllerTest.TestSecurityBeans.class})
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    // Boot 4 auto-configures Jackson 3 for web; there is no Jackson 2 ObjectMapper bean to inject.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean AuthService authService;

    @TestConfiguration
    static class TestSecurityBeans {
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            var props = new JwtProperties("test-secret-that-is-at-least-32-bytes-long!!", null, null, null);
            return new JwtAuthenticationFilter(new JwtService(props));
        }
    }

    @Test
    void registerIsPublicAndReturns201WithToken() throws Exception {
        when(authService.register(any())).thenReturn(
                AuthResponse.bearer("jwt-token", "jdoe", List.of("CUSTOMER"), "cust-1", 3600));

        var body = new RegisterRequest("jdoe", "sup3r-secret", "Jane", "Doe",
                "jane@example.com", "+15550001111", "US");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));
    }

    @Test
    void registerWithBlankUsernameReturns400() throws Exception {
        var body = new RegisterRequest("", "sup3r-secret", "Jane", "Doe",
                "jane@example.com", null, "US");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginIsPublicAndReturnsToken() throws Exception {
        when(authService.login(any())).thenReturn(
                AuthResponse.bearer("jwt-token", "jdoe", List.of("CUSTOMER"), "cust-1", 3600));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("jdoe", "sup3r-secret"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    void loginWithBadCredentialsReturns401() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("jdoe", "wrong"))))
                .andExpect(status().isUnauthorized());
    }
}
