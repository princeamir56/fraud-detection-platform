package com.frauddetect.customer.domain;

/**
 * Lifecycle of a customer profile.
 *
 * <ul>
 *   <li>{@code ACTIVE} — normal, operational customer.</li>
 *   <li>{@code BLOCKED} — access suspended, typically as a fraud response (set by an analyst /
 *       investigator). Distinct from account-level {@code FROZEN}: this blocks the whole identity.</li>
 *   <li>{@code CLOSED} — offboarded; terminal state.</li>
 * </ul>
 */
public enum CustomerStatus {
    ACTIVE,
    BLOCKED,
    CLOSED
}
