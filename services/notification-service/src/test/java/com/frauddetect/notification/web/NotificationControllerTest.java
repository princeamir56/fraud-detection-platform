package com.frauddetect.notification.web;

import com.frauddetect.common.error.GlobalExceptionHandler;
import com.frauddetect.common.error.SecurityExceptionHandler;
import com.frauddetect.common.security.JwtAuthenticationFilter;
import com.frauddetect.common.security.JwtProperties;
import com.frauddetect.common.security.JwtService;
import com.frauddetect.notification.config.SecurityConfig;
import com.frauddetect.notification.domain.NotificationChannel;
import com.frauddetect.notification.domain.NotificationEntity;
import com.frauddetect.notification.domain.NotificationStatus;
import com.frauddetect.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web-slice test covering the notification read API RBAC matrix. */
@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, SecurityExceptionHandler.class, NotificationControllerTest.TestSecurityBeans.class})
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean NotificationService service;

    @TestConfiguration
    static class TestSecurityBeans {
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            var props = new JwtProperties("test-secret-that-is-at-least-32-bytes-long!!", null, null, null);
            return new JwtAuthenticationFilter(new JwtService(props));
        }
    }

    private NotificationEntity sample() {
        NotificationEntity n = new NotificationEntity();
        n.setId("n-1");
        n.setAlertId("alert-1");
        n.setCustomerId("cust-1");
        n.setChannel(NotificationChannel.EMAIL);
        n.setRecipient("customer-cust-1@notify.example");
        n.setSubject("Fraud alert [HIGH]: Suspicious transaction");
        n.setBody("body");
        n.setSeverity("HIGH");
        n.setStatus(NotificationStatus.SENT);
        return n;
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotReadLedger() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void analystCanListNotifications() throws Exception {
        when(service.list(any())).thenReturn(new PageImpl<>(List.of(sample())));
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("n-1"))
                .andExpect(jsonPath("$.content[0].channel").value("EMAIL"));
    }

    @Test
    @WithMockUser(roles = "INVESTIGATOR")
    void investigatorCanFilterByCustomer() throws Exception {
        when(service.listByCustomer(any(), any())).thenReturn(new PageImpl<>(List.of(sample())));
        mockMvc.perform(get("/api/v1/notifications").param("customerId", "cust-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].customerId").value("cust-1"));
    }
}
