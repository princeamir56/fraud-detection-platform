package com.frauddetect.alert.messaging;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Derives stable outbound event ids from an inbound event id, so reprocessing a {@code fraud.detected}
 * record (at-least-once redelivery, or a crash between side effects and offset commit) re-derives the
 * SAME alert id and the SAME {@code alert.created} event id. Combined with the alert primary key and
 * the consumer dedupe table this keeps alert creation effectively-once without distributed transactions.
 */
public final class EventIds {

    private EventIds() {
    }

    /**
     * UUIDv3 over {@code <inbound>:<kind>}. Falls back to a random id when the inbound id is absent, so
     * a blank event id is never produced.
     *
     * @param inboundEventId the id of the consumed {@code fraud.detected} event
     * @param kind           discriminator so the alert row and the {@code alert.created} event derive distinct ids
     */
    public static String derive(String inboundEventId, String kind) {
        if (inboundEventId == null || inboundEventId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return UUID.nameUUIDFromBytes((inboundEventId + ":" + kind).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
