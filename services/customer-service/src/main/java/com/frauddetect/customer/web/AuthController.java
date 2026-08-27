package com.frauddetect.customer.web;

import com.frauddetect.customer.dto.AuthResponse;
import com.frauddetect.customer.dto.LoginRequest;
import com.frauddetect.customer.dto.RegisterRequest;
import com.frauddetect.customer.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints (Section 11). These are the only unauthenticated routes on the
 * platform — they mint the JWTs every other service validates. Both delegate to {@link AuthService};
 * the controller only wires HTTP to the service (Section 9).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
