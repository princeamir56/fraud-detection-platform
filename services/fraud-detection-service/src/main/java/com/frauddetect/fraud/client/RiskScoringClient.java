package com.frauddetect.fraud.client;

import com.frauddetect.fraud.config.RiskScoringProperties;
import com.frauddetect.fraud.feature.TransactionFeatures;
import com.frauddetect.fraud.service.TransactionContext;
import com.frauddetect.grpc.risk.RiskScoreRequest;
import com.frauddetect.grpc.risk.RiskScoreResponse;
import com.frauddetect.grpc.risk.RiskScoringServiceGrpc;
import com.frauddetect.grpc.risk.TransactionRiskFeatures;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Resilient façade over the risk-scoring gRPC call (Section 8 + 10).
 *
 * <p>Every call carries a hard {@code withDeadlineAfter} deadline, is bounded by a Resilience4j
 * {@link TimeLimiter}, and is guarded by a {@link CircuitBreaker}. If the dependency is slow,
 * failing, or the breaker is open, the client <em>degrades gracefully</em>: it returns an empty
 * score and the rule engine proceeds on rule signals alone (the {@code MODEL_RISK} rule simply does
 * not fire). Fraud detection is therefore never blocked by risk-scoring being unavailable.
 */
@Component
public class RiskScoringClient {

    private static final Logger log = LoggerFactory.getLogger(RiskScoringClient.class);

    private final RiskScoringServiceGrpc.RiskScoringServiceBlockingStub stub;
    private final CircuitBreaker circuitBreaker;
    private final TimeLimiter timeLimiter;
    private final ExecutorService executor;
    private final RiskScoringProperties props;
    private final Counter callCounter;
    private final Counter degradedCounter;

    public RiskScoringClient(RiskScoringServiceGrpc.RiskScoringServiceBlockingStub stub,
                             CircuitBreaker circuitBreaker,
                             TimeLimiter timeLimiter,
                             ExecutorService riskScoringExecutor,
                             RiskScoringProperties props,
                             MeterRegistry meterRegistry) {
        this.stub = stub;
        this.circuitBreaker = circuitBreaker;
        this.timeLimiter = timeLimiter;
        this.executor = riskScoringExecutor;
        this.props = props;
        this.callCounter = Counter.builder("fraud.risk.calls").register(meterRegistry);
        this.degradedCounter = Counter.builder("fraud.risk.degraded")
                .description("Risk-scoring calls that fell back to rule-only scoring").register(meterRegistry);
    }

    /**
     * @return the model risk sub-score in {@code [0,1]}, or empty when risk-scoring is unavailable.
     */
    public Optional<Double> score(TransactionContext ctx, TransactionFeatures features) {
        callCounter.increment();
        RiskScoreRequest request = toRequest(ctx, features);

        Callable<RiskScoreResponse> timeLimited = TimeLimiter.decorateFutureSupplier(
                timeLimiter, () -> CompletableFuture.supplyAsync(() -> invoke(request), executor));
        Callable<RiskScoreResponse> guarded = CircuitBreaker.decorateCallable(circuitBreaker, timeLimited);

        // Resilience4j 2.x no longer bundles vavr, so we bound the guarded call with a plain
        // try/catch: any failure (open circuit → CallNotPermittedException, time-limit breach,
        // gRPC error) degrades to an empty score so the rule engine proceeds on rule signals alone.
        try {
            return Optional.of(guarded.call().getRiskScore());
        } catch (Exception ex) {
            onDegraded(ctx, ex);
            return Optional.empty();
        }
    }

    /** The actual blocking gRPC invocation with a per-call deadline. */
    private RiskScoreResponse invoke(RiskScoreRequest request) {
        return stub.withDeadlineAfter(props.deadlineMs(), TimeUnit.MILLISECONDS)
                .calculateRiskScore(request);
    }

    private void onDegraded(TransactionContext ctx, Throwable ex) {
        degradedCounter.increment();
        if (ex instanceof CallNotPermittedException) {
            log.warn("Risk-scoring circuit OPEN; degrading tx {} to rule-only scoring", ctx.transactionId());
        } else {
            log.warn("Risk-scoring call failed for tx {} ({}); degrading to rule-only scoring",
                    ctx.transactionId(), ex.toString());
        }
    }

    private RiskScoreRequest toRequest(TransactionContext ctx, TransactionFeatures f) {
        TransactionRiskFeatures features = TransactionRiskFeatures.newBuilder()
                .setAmount(f.amount())
                .setCurrency(nullSafe(f.currency()))
                .setTransactionType(nullSafe(f.transactionType()))
                .setCountryCode(nullSafe(f.countryCode()))
                .setMerchantCategory(nullSafe(f.merchantCategory()))
                .setTxCountLastHour(f.txCountLastHour())
                .setTxCountLast24H(f.txCountLast24h())
                .setAmountSumLast24H(f.amountSumLast24h())
                .setAvgAmountLast30D(f.avgAmount30d())
                .setDistinctCountriesLast24H(f.distinctCountriesLast24h())
                .setNewDevice(f.newDevice())
                .setNewMerchant(f.newMerchant())
                .setFailedTxLastHour(f.failedTxLastHour())
                .setKmFromLastTx(f.kmFromLastTx())
                .setSecondsSinceLastTx(f.secondsSinceLastTx())
                .build();

        return RiskScoreRequest.newBuilder()
                .setCorrelationId(nullSafe(ctx.correlationId()))
                .setTransactionId(nullSafe(ctx.transactionId()))
                .setCustomerId(nullSafe(ctx.customerId()))
                .setAccountId(nullSafe(ctx.accountId()))
                .setFeatures(features)
                .build();
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
