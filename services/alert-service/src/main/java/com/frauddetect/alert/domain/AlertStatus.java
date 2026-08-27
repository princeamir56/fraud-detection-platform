package com.frauddetect.alert.domain;

/**
 * Lifecycle of a fraud alert / case. An alert opens {@code OPEN} on {@code fraud.detected},
 * transitions to {@code ACKNOWLEDGED} when an investigator picks it up, and terminates at
 * {@code RESOLVED} when a resolution is recorded (emitting {@code alert.resolved}).
 */
public enum AlertStatus {
    OPEN,
    ACKNOWLEDGED,
    RESOLVED
}
