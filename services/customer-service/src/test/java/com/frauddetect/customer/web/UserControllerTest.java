package com.frauddetect.customer.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frauddetect.common.error.GlobalExceptionHandler;
import com.frauddetect.common.security.JwtAuthenticationFilter;
import com.frauddetect.common.security.JwtProperties;
import com.frauddetect.common.security.JwtService;
import com.frauddetect.customer.config.SecurityConfig;
import com.frauddetect.customer.dto.CreateUserRequest;
import com.frauddetect.customer.dto.UserResponse;
import com.frauddetect.customer.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web-slice test for administrative user creation — only ADMIN may reach it. */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, UserControllerTest.TestSecurityBeans.class})
class UserControllerTest {

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

    private CreateUserRequest validRequest() {
        return new CreateUserRequest("analyst1", "analyst-pass", List.of("ANALYST"));
    }

    @Test
    void unauthenticatedCreateIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void nonAdminCannotCreateUsers() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanCreateStaffUser() throws Exception {
        when(authService.createStaffUser(any())).thenReturn(
                new UserResponse("u-1", "analyst1", List.of("ANALYST"), true, null, Instant.now()));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value("u-1"))
                .andExpect(jsonPath("$.roles[0]").value("ANALYST"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void emptyRolesRejectedWith400() throws Exception {
        var bad = new CreateUserRequest("analyst1", "analyst-pass", List.of());
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }
}
