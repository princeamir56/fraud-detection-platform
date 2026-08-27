package com.frauddetect.audit.web;

import com.frauddetect.audit.config.SecurityConfig;
import com.frauddetect.audit.search.AuditQuery;
import com.frauddetect.audit.search.AuditSearchService;
import com.frauddetect.audit.search.SearchResults;
import com.frauddetect.common.security.JwtAuthenticationFilter;
import com.frauddetect.common.security.JwtProperties;
import com.frauddetect.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-slice test for the compliance search RBAC matrix. The audit trail is restricted to
 * INVESTIGATOR/ADMIN; ANALYST and CUSTOMER are explicitly denied even though ANALYST may use the fraud
 * search API on the fraud-detection service.
 */
@WebMvcTest(AuditController.class)
@Import({SecurityConfig.class, AuditControllerTest.TestSecurityBeans.class})
class AuditControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean AuditSearchService searchService;

    @TestConfiguration
    static class TestSecurityBeans {
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            var props = new JwtProperties("test-secret-that-is-at-least-32-bytes-long!!", null, null, null);
            return new JwtAuthenticationFilter(new JwtService(props));
        }
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/audit/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotReadAuditTrail() throws Exception {
        mockMvc.perform(get("/api/v1/audit/events"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void analystCannotReadAuditTrail() throws Exception {
        mockMvc.perform(get("/api/v1/audit/events"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "INVESTIGATOR")
    void investigatorCanSearchAuditTrail() throws Exception {
        when(searchService.search(any(AuditQuery.class))).thenReturn(SearchResults.empty(0, 20));
        mockMvc.perform(get("/api/v1/audit/events").param("correlationId", "corr-1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanSearchAuditTrail() throws Exception {
        when(searchService.search(any(AuditQuery.class))).thenReturn(SearchResults.empty(0, 20));
        mockMvc.perform(get("/api/v1/audit/events"))
                .andExpect(status().isOk());
    }
}
