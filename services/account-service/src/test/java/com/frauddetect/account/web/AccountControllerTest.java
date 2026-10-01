package com.frauddetect.account.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frauddetect.account.domain.AccountStatus;
import com.frauddetect.account.domain.AccountType;
import com.frauddetect.account.dto.AccountResponse;
import com.frauddetect.account.dto.CreateAccountRequest;
import com.frauddetect.account.service.AccountService;
import com.frauddetect.common.error.GlobalExceptionHandler;
import com.frauddetect.common.error.SecurityExceptionHandler;
import com.frauddetect.common.error.ResourceNotFoundException;
import com.frauddetect.common.security.JwtAuthenticationFilter;
import com.frauddetect.common.security.JwtProperties;
import com.frauddetect.common.security.JwtService;
import com.frauddetect.account.config.SecurityConfig;
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

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, SecurityExceptionHandler.class, AccountControllerTest.TestSecurityBeans.class})
class AccountControllerTest {

    @Autowired MockMvc mockMvc;

    // Boot 4 auto-configures Jackson 3 (tools.jackson JsonMapper) for web, so there is no
    // com.fasterxml.jackson ObjectMapper bean to inject. This test only needs to serialize
    // request DTOs to JSON strings, so it uses its own Jackson 2 mapper.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean AccountService service;

    @TestConfiguration
    static class TestSecurityBeans {
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            var props = new JwtProperties("test-secret-that-is-at-least-32-bytes-long!!", null, null, null);
            return new JwtAuthenticationFilter(new JwtService(props));
        }
    }

    private CreateAccountRequest validRequest() {
        return new CreateAccountRequest("cust-1", null, AccountType.CHECKING, "USD",
                new BigDecimal("100.00"), null);
    }

    private AccountResponse sample() {
        return new AccountResponse("acc-1", "cust-1", "FD000000000001", AccountType.CHECKING, "USD",
                new BigDecimal("100.00"), BigDecimal.ZERO, AccountStatus.ACTIVE, Instant.now(),
                Instant.now(), Instant.now(), 0L);
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanOpenAccount() throws Exception {
        when(service.create(any())).thenReturn(sample());

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value("acc-1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotOpenAccount() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidCurrencyIsRejectedWith400() throws Exception {
        var bad = new CreateAccountRequest("cust-1", null, AccountType.CHECKING, "usd",
                new BigDecimal("100.00"), null);
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void missingAccountReturns404() throws Exception {
        when(service.getById("nope")).thenThrow(new ResourceNotFoundException("Account not found: nope"));
        mockMvc.perform(get("/api/v1/accounts/{id}", "nope"))
                .andExpect(status().isNotFound());
    }
}
