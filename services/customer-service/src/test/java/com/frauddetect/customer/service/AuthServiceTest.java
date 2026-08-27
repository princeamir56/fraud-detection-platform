package com.frauddetect.customer.service;

import com.frauddetect.common.error.BusinessRuleException;
import com.frauddetect.common.error.ConflictException;
import com.frauddetect.common.security.JwtProperties;
import com.frauddetect.common.security.JwtService;
import com.frauddetect.customer.domain.UserEntity;
import com.frauddetect.customer.dto.AuthResponse;
import com.frauddetect.customer.dto.CreateUserRequest;
import com.frauddetect.customer.dto.LoginRequest;
import com.frauddetect.customer.dto.RegisterRequest;
import com.frauddetect.customer.dto.UserResponse;
import com.frauddetect.customer.repository.CustomerRepository;
import com.frauddetect.customer.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository users;
    @Mock CustomerRepository customers;
    @Mock PasswordEncoder passwordEncoder;

    AuthService service;

    @BeforeEach
    void setUp() {
        // Real JWT collaborators — cheap, deterministic, and exercise the actual signing path.
        JwtProperties props = new JwtProperties(
                "test-secret-that-is-at-least-32-bytes-long!!", "test-issuer",
                Duration.ofHours(1), Duration.ofSeconds(30));
        service = new AuthService(users, customers, passwordEncoder, new JwtService(props), props);
    }

    private RegisterRequest registerRequest() {
        return new RegisterRequest("jdoe", "sup3r-secret", "Jane", "Doe",
                "jane@example.com", "+15550001111", "US");
    }

    @Test
    void registerHashesPasswordProvisionsProfileAndReturnsToken() {
        when(users.existsByUsername("jdoe")).thenReturn(false);
        when(customers.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("sup3r-secret")).thenReturn("$2a$hashed");

        AuthResponse response = service.register(registerRequest());

        assertThat(response.token()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.subject()).isEqualTo("jdoe");
        assertThat(response.roles()).containsExactly("CUSTOMER");
        assertThat(response.customerId()).isNotBlank();
        assertThat(response.expiresInSeconds()).isEqualTo(3600);

        // Password is hashed, never stored raw.
        verify(passwordEncoder).encode("sup3r-secret");
        verify(customers).save(any());
        verify(users).save(any(UserEntity.class));
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(users.existsByUsername("jdoe")).thenReturn(true);

        assertThatThrownBy(() -> service.register(registerRequest()))
                .isInstanceOf(ConflictException.class);
        verify(users, never()).save(any());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(users.existsByUsername("jdoe")).thenReturn(false);
        when(customers.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(registerRequest()))
                .isInstanceOf(ConflictException.class);
        verify(customers, never()).save(any());
    }

    @Test
    void loginSucceedsWithCorrectPassword() {
        UserEntity user = user("jdoe", "$2a$hashed", "CUSTOMER", true);
        when(users.findByUsername("jdoe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("sup3r-secret", "$2a$hashed")).thenReturn(true);

        AuthResponse response = service.login(new LoginRequest("jdoe", "sup3r-secret"));

        assertThat(response.token()).isNotBlank();
        assertThat(response.roles()).containsExactly("CUSTOMER");
    }

    @Test
    void loginFailsForUnknownUser() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("ghost", "x")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginFailsOnWrongPassword() {
        UserEntity user = user("jdoe", "$2a$hashed", "CUSTOMER", true);
        when(users.findByUsername("jdoe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("nope", "$2a$hashed")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("jdoe", "nope")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginFailsForDisabledUser() {
        UserEntity user = user("jdoe", "$2a$hashed", "CUSTOMER", false);
        when(users.findByUsername("jdoe")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new LoginRequest("jdoe", "sup3r-secret")))
                .isInstanceOf(InvalidCredentialsException.class);
        // A disabled account must never even reach the password check.
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void createStaffUserPersistsRequestedRoles() {
        when(users.existsByUsername("analyst1")).thenReturn(false);
        when(passwordEncoder.encode("analyst-pass")).thenReturn("$2a$staff");
        when(users.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.createStaffUser(
                new CreateUserRequest("analyst1", "analyst-pass", List.of("ANALYST")));

        assertThat(response.username()).isEqualTo("analyst1");
        assertThat(response.roles()).containsExactly("ANALYST");
        assertThat(response.customerId()).isNull();
    }

    @Test
    void createStaffUserRejectsUnknownRole() {
        assertThatThrownBy(() -> service.createStaffUser(
                new CreateUserRequest("bad", "password1", List.of("SUPERUSER"))))
                .isInstanceOf(BusinessRuleException.class);
        verify(users, never()).save(any());
    }

    @Test
    void createStaffUserRejectsDuplicateUsername() {
        when(users.existsByUsername("dupe")).thenReturn(true);

        assertThatThrownBy(() -> service.createStaffUser(
                new CreateUserRequest("dupe", "password1", List.of("ADMIN"))))
                .isInstanceOf(ConflictException.class);
        verify(users, never()).save(any());
    }

    private UserEntity user(String username, String hash, String roles, boolean enabled) {
        UserEntity e = new UserEntity();
        e.setId("u-1");
        e.setUsername(username);
        e.setPasswordHash(hash);
        e.setRoles(roles);
        e.setEnabled(enabled);
        return e;
    }
}
