package com.frauddetect.fraud.messaging;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Derives stable outbound event ids from the inbound event id, so reprocessing a
 * {@code transaction.created} record (at-least-once redelivery, or a crash between side effects and
 * offset commit) re-emits events carrying the SAME id. Downstream consumers dedupe on that id and the
 * Elasticsearch fraud-event document is written to the same doc id — making the pipeline
 * effectively-once end to end without distributed transactions.
 */
public final class EventIds {

    private EventIds() {
    }

    /**
     * UUIDv3 over {@code <inbound>:<kind>}. Falls back to a random id when the inbound id is absent,
     * so we never emit a blank event id.
     *
     * @param inboundEventId the id of the consumed {@code transaction.created} event
     * @param kind           discriminator so the score and detected events derive distinct ids
     */
    public static String derive(String inboundEventId, String kind) {
        if (inboundEventId == null || inboundEventId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return UUID.nameUUIDFromBytes((inboundEventId + ":" + kind).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
