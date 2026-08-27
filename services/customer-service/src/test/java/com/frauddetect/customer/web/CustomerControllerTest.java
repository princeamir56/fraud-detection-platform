package com.frauddetect.customer.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frauddetect.common.error.GlobalExceptionHandler;
import com.frauddetect.common.error.ResourceNotFoundException;
import com.frauddetect.common.security.JwtAuthenticationFilter;
import com.frauddetect.common.security.JwtProperties;
import com.frauddetect.common.security.JwtService;
import com.frauddetect.customer.config.SecurityConfig;
import com.frauddetect.customer.domain.CustomerStatus;
import com.frauddetect.customer.dto.CustomerResponse;
import com.frauddetect.customer.dto.UpdateCustomerRequest;
import com.frauddetect.customer.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web-slice test covering the customer profile RBAC matrix and not-found handling. */
@WebMvcTest(CustomerController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, CustomerControllerTest.TestSecurityBeans.class})
class CustomerControllerTest {

    @Autowired MockMvc mockMvc;
    // Boot 4 auto-configures Jackson 3 for web; there is no Jackson 2 ObjectMapper bean to inject.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean CustomerService service;

    @TestConfiguration
    static class TestSecurityBeans {
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            var props = new JwtProperties("test-secret-that-is-at-least-32-bytes-long!!", null, null, null);
            return new JwtAuthenticationFilter(new JwtService(props));
        }
    }

    private CustomerResponse sample() {
        return new CustomerResponse("cust-1", "Jane", "Doe", "jane@example.com", null, "US",
                CustomerStatus.ACTIVE, Instant.now(), Instant.now(), 0L);
    }

    @Test
    void unauthenticatedReadIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{id}", "cust-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void anyAuthenticatedUserCanReadById() throws Exception {
        when(service.getById("cust-1")).thenReturn(sample());
        mockMvc.perform(get("/api/v1/customers/{id}", "cust-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("cust-1"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotListAllCustomers() throws Exception {
        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void analystCanListCustomers() throws Exception {
        when(service.list(any(), any())).thenReturn(new PageImpl<>(List.of(sample())));
        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("cust-1"));
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void missingCustomerReturns404() throws Exception {
        when(service.getById("nope")).thenThrow(new ResourceNotFoundException("Customer not found: nope"));
        mockMvc.perform(get("/api/v1/customers/{id}", "nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotEditProfiles() throws Exception {
        var body = new UpdateCustomerRequest("Jane", "Doe", "+15550001111");
        mockMvc.perform(put("/api/v1/customers/{id}", "cust-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanEditProfile() throws Exception {
        when(service.update(eq("cust-1"), any())).thenReturn(sample());
        var body = new UpdateCustomerRequest("Jane", "Doe", "+15550001111");
        mockMvc.perform(put("/api/v1/customers/{id}", "cust-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }
}
