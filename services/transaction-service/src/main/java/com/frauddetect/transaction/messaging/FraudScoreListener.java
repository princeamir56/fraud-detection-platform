package com.frauddetect.transaction.messaging;

import com.frauddetect.avro.events.FraudScoreCalculated;
import com.frauddetect.common.constants.KafkaTopics;
import com.frauddetect.common.correlation.CorrelationContext;
import com.frauddetect.transaction.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code fraud.score.calculated} and finalises the transaction. Correlation id is restored
 * from the Avro envelope into the MDC for the duration of processing. Failures propagate to the
 * container's {@link org.springframework.kafka.listener.DefaultErrorHandler} for retry + DLT.
 */
@Component
public class FraudScoreListener {

    private static final Logger log = LoggerFactory.getLogger(FraudScoreListener.class);

    private final TransactionService transactionService;

    public FraudScoreListener(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @KafkaListener(topics = KafkaTopics.FRAUD_SCORE_CALCULATED, containerFactory = "kafkaListenerContainerFactory")
    public void onFraudScore(FraudScoreCalculated event) {
        CorrelationContext.setCorrelationId(event.getCorrelationId());
        try {
            log.debug("Received fraud verdict tx={} score={} severity={} decision={}",
                    event.getTransactionId(), event.getScore(), event.getSeverity(), event.getDecision());
            transactionService.applyFraudVerdict(event);
        } finally {
            CorrelationContext.clear();
        }
    }
}
