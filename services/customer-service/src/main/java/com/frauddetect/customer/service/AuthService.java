package com.frauddetect.customer.service;

import com.frauddetect.common.constants.SecurityRoles;
import com.frauddetect.common.error.BusinessRuleException;
import com.frauddetect.common.error.ConflictException;
import com.frauddetect.common.security.JwtProperties;
import com.frauddetect.common.security.JwtService;
import com.frauddetect.customer.domain.CustomerEntity;
import com.frauddetect.customer.domain.CustomerStatus;
import com.frauddetect.customer.domain.UserEntity;
import com.frauddetect.customer.dto.AuthResponse;
import com.frauddetect.customer.dto.CreateUserRequest;
import com.frauddetect.customer.dto.LoginRequest;
import com.frauddetect.customer.dto.RegisterRequest;
import com.frauddetect.customer.dto.UserResponse;
import com.frauddetect.customer.repository.CustomerRepository;
import com.frauddetect.customer.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Authentication + user administration (Section 11). Owns credential hashing (BCrypt), JWT issuance
 * and the registration flow that provisions a customer profile alongside its login. All password
 * comparisons go through {@link PasswordEncoder}; plaintext is never persisted or logged.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** Roles a staff account may be granted via the admin API. */
    private static final Set<String> ASSIGNABLE_ROLES = Set.of(
            SecurityRoles.ADMIN, SecurityRoles.ANALYST, SecurityRoles.INVESTIGATOR, SecurityRoles.CUSTOMER);

    private final UserRepository users;
    private final CustomerRepository customers;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthService(UserRepository users, CustomerRepository customers, PasswordEncoder passwordEncoder,
                       JwtService jwtService, JwtProperties jwtProperties) {
        this.users = users;
        this.customers = customers;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    /** Self-service registration: creates a profile + a {@code CUSTOMER} login and returns a token. */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (users.existsByUsername(req.username())) {
            throw new ConflictException("Username already taken: " + req.username());
        }
        if (customers.existsByEmail(req.email())) {
            throw new ConflictException("Email already registered: " + req.email());
        }

        CustomerEntity customer = new CustomerEntity();
        customer.setId(UUID.randomUUID().toString());
        customer.setFirstName(req.firstName());
        customer.setLastName(req.lastName());
        customer.setEmail(req.email());
        customer.setPhone(req.phone());
        customer.setCountryCode(req.countryCode());
        customer.setStatus(CustomerStatus.ACTIVE);
        customers.save(customer);

        List<String> roles = List.of(SecurityRoles.CUSTOMER);
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID().toString());
        user.setUsername(req.username());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRoleList(roles);
        user.setEnabled(true);
        user.setCustomerId(customer.getId());
        users.save(user);

        log.info("Registered customer profile={} username={}", customer.getId(), req.username());
        return issueToken(user.getUsername(), roles, customer.getId());
    }

    /** Verify credentials and mint an access token, or fail with a generic 401. */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        UserEntity user = users.findByUsername(req.username())
                .orElseThrow(InvalidCredentialsException::new);
        if (!user.isEnabled() || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        log.info("Authenticated username={}", user.getUsername());
        return issueToken(user.getUsername(), user.roleList(), user.getCustomerId());
    }

    /** Admin-only creation of a staff user with explicit roles. */
    @Transactional
    public UserResponse createStaffUser(CreateUserRequest req) {
        List<String> roles = normalizeRoles(req.roles());
        if (users.existsByUsername(req.username())) {
            throw new ConflictException("Username already taken: " + req.username());
        }

        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID().toString());
        user.setUsername(req.username());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRoleList(roles);
        user.setEnabled(true);
        // Staff users are not tied to a customer profile.
        user.setCustomerId(null);
        UserEntity saved = users.save(user);

        log.info("Created staff user={} roles={}", saved.getUsername(), roles);
        return UserResponse.from(saved);
    }

    private List<String> normalizeRoles(List<String> requested) {
        List<String> normalized = requested.stream()
                .map(r -> r.trim().toUpperCase())
                .distinct()
                .toList();
        for (String role : normalized) {
            if (!ASSIGNABLE_ROLES.contains(role)) {
                throw new BusinessRuleException("Unknown role: " + role);
            }
        }
        return normalized;
    }

    private AuthResponse issueToken(String subject, List<String> roles, String customerId) {
        String token = jwtService.generateToken(subject, roles);
        return AuthResponse.bearer(token, subject, roles, customerId,
                jwtProperties.accessTokenTtl().toSeconds());
    }
}
