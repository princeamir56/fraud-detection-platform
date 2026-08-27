package com.frauddetect.fraud.config;

import com.frauddetect.fraud.client.CorrelationClientInterceptor;
import com.frauddetect.grpc.risk.RiskScoringServiceGrpc;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Wires the gRPC channel to risk-scoring-service and the Resilience4j primitives that guard the hot
 * path (Section 10). The blocking stub is used with a per-call deadline; the circuit breaker trips
 * on sustained failures so we stop hammering a sick dependency, and the time limiter bounds the
 * wall-clock time even if the channel stalls before the gRPC deadline fires.
 */
@Configuration(proxyBeanMethods = false)
public class GrpcClientConfig {

    private static final Logger log = LoggerFactory.getLogger(GrpcClientConfig.class);

    public static final String RISK_CIRCUIT_BREAKER = "riskScoring";

    /**
     * A single long-lived channel (gRPC multiplexes many calls over one HTTP/2 connection).
     * Closed on context shutdown.
     */
    @Bean(destroyMethod = "shutdownNow")
    public ManagedChannel riskScoringChannel(RiskScoringProperties props) {
        log.info("Creating gRPC channel to risk-scoring at {}:{} (plaintext={})",
                props.host(), props.port(), props.plaintext());
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder
                .forAddress(props.host(), props.port())
                .intercept(new CorrelationClientInterceptor());
        if (props.plaintext()) {
            builder.usePlaintext();
        }
        return builder.build();
    }

    @Bean
    public RiskScoringServiceGrpc.RiskScoringServiceBlockingStub riskScoringStub(ManagedChannel riskScoringChannel) {
        return RiskScoringServiceGrpc.newBlockingStub(riskScoringChannel);
    }

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(MeterRegistry meterRegistry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        // Expose resilience4j.circuitbreaker.* metrics via Micrometer/Prometheus.
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meterRegistry);
        return registry;
    }

    @Bean
    public CircuitBreaker riskScoringCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker(RISK_CIRCUIT_BREAKER);
    }

    @Bean
    public TimeLimiter riskScoringTimeLimiter(RiskScoringProperties props) {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                // A little headroom over the gRPC deadline so the deadline (not the limiter) usually wins.
                .timeoutDuration(Duration.ofMillis(props.deadlineMs() + 100))
                .cancelRunningFuture(true)
                .build();
        return TimeLimiter.of("riskScoring", config);
    }

    /**
     * Bounded executor backing the time limiter — also acts as a bulkhead capping concurrent
     * in-flight gRPC calls so a slow dependency cannot exhaust the listener threads.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService riskScoringExecutor() {
        return Executors.newFixedThreadPool(16, r -> {
            Thread t = new Thread(r, "risk-grpc");
            t.setDaemon(true);
            return t;
        });
    }
}
