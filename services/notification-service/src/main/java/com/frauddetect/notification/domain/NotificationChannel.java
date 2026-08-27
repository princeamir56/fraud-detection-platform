package com.frauddetect.notification.domain;

/** Delivery channel for a notification. Mocked delivery only — no real gateway is contacted. */
public enum NotificationChannel {
    EMAIL,
    SMS,
    PUSH
}
