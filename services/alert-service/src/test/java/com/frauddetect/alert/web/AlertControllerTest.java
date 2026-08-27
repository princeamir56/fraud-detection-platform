package com.frauddetect.alert.web;

import com.frauddetect.alert.config.SecurityConfig;
import com.frauddetect.alert.domain.AlertEntity;
import com.frauddetect.alert.domain.AlertStatus;
import com.frauddetect.alert.domain.Resolution;
import com.frauddetect.alert.search.AlertSearchService;
import com.frauddetect.alert.service.AlertService;
import com.frauddetect.common.security.JwtAuthenticationFilter;
import com.frauddetect.common.security.JwtProperties;
import com.frauddetect.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-slice test for the alert triage RBAC matrix (Section 11). Reads (list) are open to
 * ANALYST/INVESTIGATOR/ADMIN; the state-changing triage actions (acknowledge, resolve) are restricted
 * to INVESTIGATOR/ADMIN, so an ANALYST — who can view alerts — cannot resolve them.
 */
@WebMvcTest(AlertController.class)
@Import({SecurityConfig.class, AlertControllerTest.TestSecurityBeans.class})
class AlertControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean AlertService alertService;
    @MockitoBean AlertSearchService searchService;

    @TestConfiguration
    static class TestSecurityBeans {
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            var props = new JwtProperties("test-secret-that-is-at-least-32-bytes-long!!", null, null, null);
            return new JwtAuthenticationFilter(new JwtService(props));
        }
    }

    private static AlertEntity sampleAlert() {
        AlertEntity a = new AlertEntity();
        a.setId("alert-1");
        a.setTransactionId("tx-1");
        a.setCustomerId("cust-1");
        a.setAccountId("acc-1");
        a.setSeverity("HIGH");
        a.setScore(85);
        a.setStatus(AlertStatus.RESOLVED);
        a.setTitle("HIGH-severity fraud on transaction tx-1");
        a.setDescription("desc");
        return a;
    }

    // ---- list (read) ----

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/alerts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotListAlerts() throws Exception {
        mockMvc.perform(get("/api/v1/alerts"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void analystCanListAlerts() throws Exception {
        when(alertService.list(any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());
        mockMvc.perform(get("/api/v1/alerts"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "INVESTIGATOR")
    void investigatorCanListAlerts() throws Exception {
        when(alertService.list(any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());
        mockMvc.perform(get("/api/v1/alerts"))
                .andExpect(status().isOk());
    }

    // ---- resolve (triage) ----

    @Test
    @WithMockUser(roles = "ANALYST")
    void analystCannotResolveAlert() throws Exception {
        mockMvc.perform(post("/api/v1/alerts/alert-1/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\"FALSE_POSITIVE\",\"notes\":\"looks fine\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "INVESTIGATOR")
    void investigatorCanResolveAlert() throws Exception {
        when(alertService.resolve(any(), any(Resolution.class), any(), any())).thenReturn(sampleAlert());
        mockMvc.perform(post("/api/v1/alerts/alert-1/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\"CONFIRMED_FRAUD\",\"notes\":\"chargeback confirmed\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanResolveAlert() throws Exception {
        when(alertService.resolve(any(), any(Resolution.class), any(), any())).thenReturn(sampleAlert());
        mockMvc.perform(post("/api/v1/alerts/alert-1/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\"DISMISSED\"}"))
                .andExpect(status().isOk());
    }

    // ---- acknowledge (triage) ----

    @Test
    @WithMockUser(roles = "ANALYST")
    void analystCannotAcknowledgeAlert() throws Exception {
        mockMvc.perform(post("/api/v1/alerts/alert-1/acknowledge"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "INVESTIGATOR")
    void investigatorCanAcknowledgeAlert() throws Exception {
        when(alertService.acknowledge(any(), any())).thenReturn(sampleAlert());
        mockMvc.perform(post("/api/v1/alerts/alert-1/acknowledge"))
                .andExpect(status().isOk());
    }
}
