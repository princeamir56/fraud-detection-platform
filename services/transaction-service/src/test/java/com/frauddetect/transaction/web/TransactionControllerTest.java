package com.frauddetect.transaction.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frauddetect.common.domain.TransactionStatus;
import com.frauddetect.common.domain.TransactionType;
import com.frauddetect.common.error.GlobalExceptionHandler;
import com.frauddetect.common.error.ResourceNotFoundException;
import com.frauddetect.common.security.JwtAuthenticationFilter;
import com.frauddetect.common.security.JwtProperties;
import com.frauddetect.common.security.JwtService;
import com.frauddetect.transaction.config.SecurityConfig;
import com.frauddetect.transaction.dto.CreateTransactionRequest;
import com.frauddetect.transaction.dto.TransactionResponse;
import com.frauddetect.transaction.service.TransactionService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, TransactionControllerTest.TestSecurityBeans.class})
class TransactionControllerTest {

    @Autowired MockMvc mockMvc;
    // Boot 4 auto-configures Jackson 3 for web; there is no Jackson 2 ObjectMapper bean to inject.
    // This test only needs to serialize request bodies, so a plain instance is sufficient.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean TransactionService service;

    @TestConfiguration
    static class TestSecurityBeans {
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            var props = new JwtProperties("test-secret-that-is-at-least-32-bytes-long!!", null, null, null);
            return new JwtAuthenticationFilter(new JwtService(props));
        }
    }

    private CreateTransactionRequest validRequest() {
        return new CreateTransactionRequest("acc-1", "cust-1", new BigDecimal("125.50"), "USD",
                TransactionType.PURCHASE, null, null, "US", null, null, null, null, null, "WEB");
    }

    private TransactionResponse pending() {
        return new TransactionResponse("tx-1", "acc-1", "cust-1", new BigDecimal("125.50"), "USD",
                TransactionType.PURCHASE, TransactionStatus.PENDING, null, null, "US", null, "WEB",
                null, null, null, null, null, Instant.now(), Instant.now());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCanCreateTransaction() throws Exception {
        when(service.create(any(), eq(null))).thenReturn(pending());

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value("tx-1"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(roles = "INVESTIGATOR")
    void investigatorCannotCreateTransaction() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void invalidRequestIsRejectedWith400() throws Exception {
        var bad = new CreateTransactionRequest("acc-1", "cust-1", new BigDecimal("-5"), "usd",
                TransactionType.PURCHASE, null, null, "USA", null, null, null, null, null, "WEB");

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void missingTransactionReturns404() throws Exception {
        when(service.getById("nope")).thenThrow(new ResourceNotFoundException("Transaction not found: nope"));

        mockMvc.perform(get("/api/v1/transactions/{id}", "nope"))
                .andExpect(status().isNotFound());
    }
}
