package com.frauddetect.risk.grpc;

import com.frauddetect.grpc.risk.RiskScoreRequest;
import com.frauddetect.grpc.risk.RiskScoreResponse;
import com.frauddetect.grpc.risk.RiskScoringServiceGrpc;
import com.frauddetect.grpc.risk.TransactionRiskFeatures;
import com.frauddetect.risk.scoring.RiskModel;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * gRPC integration test over an in-process transport — exercises the real generated stubs,
 * marshalling and service implementation without opening a socket.
 */
class RiskScoringServiceImplIT {

    private Server server;
    private ManagedChannel channel;
    private RiskScoringServiceGrpc.RiskScoringServiceBlockingStub stub;

    @BeforeEach
    void setUp() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(new RiskScoringServiceImpl(new RiskModel(), new SimpleMeterRegistry()))
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        stub = RiskScoringServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws Exception {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void returnsScoreAndBandOverTheWire() {
        RiskScoreResponse response = stub.calculateRiskScore(RiskScoreRequest.newBuilder()
                .setCorrelationId("test-corr")
                .setTransactionId("tx-1")
                .setCustomerId("cust-1")
                .setAccountId("acc-1")
                .setFeatures(TransactionRiskFeatures.newBuilder()
                        .setAmount(9000).setCurrency("USD").setTransactionType("WITHDRAWAL")
                        .setCountryCode("NG").setTxCountLastHour(15).setAvgAmountLast30D(50)
                        .setDistinctCountriesLast24H(5).setNewDevice(true).setFailedTxLastHour(5)
                        .build())
                .build());

        assertThat(response.getTransactionId()).isEqualTo("tx-1");
        assertThat(response.getRiskScore()).isBetween(0.0, 1.0);
        assertThat(response.getRiskBand()).isEqualTo("CRITICAL");
        assertThat(response.getContributingFactorsList()).isNotEmpty();
        assertThat(response.hasScoredAt()).isTrue();
    }
}
