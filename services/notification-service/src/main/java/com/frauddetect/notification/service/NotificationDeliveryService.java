package com.frauddetect.notification.service;

import com.frauddetect.notification.domain.NotificationEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Mocked delivery gateway (Section 3). In a real deployment this would front SMTP / an SMS provider /
 * a push service; here it logs the send and reports success so the rest of the pipeline (ledger
 * persistence, status transitions, dedupe, observability) is fully exercised without an external
 * dependency. Delivery is deliberately side-effect-free and deterministic so tests are stable.
 */
@Service
public class NotificationDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);

    /**
     * "Delivers" the notification. Returns {@code true} on success; a real implementation would
     * translate transport failures into {@code false} (or throw for retryable faults).
     */
    public boolean deliver(NotificationEntity notification) {
        log.info("MOCK-DELIVER channel={} to={} severity={} subject='{}'",
                notification.getChannel(), notification.getRecipient(),
                notification.getSeverity(), notification.getSubject());
        return true;
    }
}
