package com.frauddetect.common.constants;

/**
 * RBAC role catalogue (Section 11). Spring Security authorities are stored WITHOUT the
 * {@code ROLE_} prefix in the JWT {@code roles} claim; the {@code ROLE_*} constants are the
 * authority form used by {@code hasRole(...)} / {@code @PreAuthorize}.
 */
public final class SecurityRoles {

    private SecurityRoles() {
    }

    public static final String ADMIN = "ADMIN";
    public static final String ANALYST = "ANALYST";
    public static final String INVESTIGATOR = "INVESTIGATOR";
    public static final String CUSTOMER = "CUSTOMER";

    public static final String ROLE_ADMIN = "ROLE_" + ADMIN;
    public static final String ROLE_ANALYST = "ROLE_" + ANALYST;
    public static final String ROLE_INVESTIGATOR = "ROLE_" + INVESTIGATOR;
    public static final String ROLE_CUSTOMER = "ROLE_" + CUSTOMER;
}
