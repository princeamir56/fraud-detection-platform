package com.frauddetect.notification.domain;

/** Lifecycle of a single notification delivery attempt. */
public enum NotificationStatus {
    /** Persisted but not yet dispatched. */
    PENDING,
    /** Successfully handed to the (mocked) delivery channel. */
    SENT,
    /** Delivery attempt failed; retained for audit / manual replay. */
    FAILED
}
