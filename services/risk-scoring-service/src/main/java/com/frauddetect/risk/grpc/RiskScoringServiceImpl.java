package com.frauddetect.risk.grpc;

import com.frauddetect.grpc.risk.RiskScoreRequest;
import com.frauddetect.grpc.risk.RiskScoreResponse;
import com.frauddetect.grpc.risk.RiskScoringServiceGrpc;
import com.frauddetect.risk.scoring.RiskModel;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * gRPC endpoint implementing {@code RiskScoringService.CalculateRiskScore}. Stateless: it turns the
 * supplied {@link com.frauddetect.grpc.risk.TransactionRiskFeatures} into a risk score via
 * {@link RiskModel}. Registered as a {@link io.grpc.BindableService} bean and bound by
 * {@link GrpcServerRunner}.
 */
@Component
public class RiskScoringServiceImpl extends RiskScoringServiceGrpc.RiskScoringServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(RiskScoringServiceImpl.class);

    private final RiskModel model;
    private final Timer scoringTimer;

    public RiskScoringServiceImpl(RiskModel model, MeterRegistry meterRegistry) {
        this.model = model;
        this.scoringTimer = Timer.builder("risk.scoring.duration")
                .description("Latency of gRPC risk score calculation")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    @Override
    public void calculateRiskScore(RiskScoreRequest request, StreamObserver<RiskScoreResponse> responseObserver) {
        Timer.Sample sample = Timer.start();
        try {
            if (!request.hasFeatures()) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("features are required")
                        .asRuntimeException());
                return;
            }

            RiskModel.RiskAssessment assessment = model.score(request.getFeatures());
            Instant now = Instant.now();

            RiskScoreResponse response = RiskScoreResponse.newBuilder()
                    .setTransactionId(request.getTransactionId())
                    .setRiskScore(assessment.score())
                    .setRiskBand(assessment.band())
                    .addAllContributingFactors(assessment.factors())
                    .setModelVersion(assessment.modelVersion())
                    .setScoredAt(Timestamp.newBuilder()
                            .setSeconds(now.getEpochSecond())
                            .setNanos(now.getNano())
                            .build())
                    .build();

            log.debug("Scored tx {} -> {} ({})", request.getTransactionId(), assessment.score(), assessment.band());
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (RuntimeException ex) {
            log.error("Risk scoring failed for tx {}", request.getTransactionId(), ex);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("scoring failure")
                    .withCause(ex)
                    .asRuntimeException());
        } finally {
            sample.stop(scoringTimer);
        }
    }
}
