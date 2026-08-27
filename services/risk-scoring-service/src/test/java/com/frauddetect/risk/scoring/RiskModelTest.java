package com.frauddetect.risk.scoring;

import com.frauddetect.grpc.risk.TransactionRiskFeatures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RiskModelTest {

    private final RiskModel model = new RiskModel();

    @Test
    void ordinaryTransactionScoresLow() {
        TransactionRiskFeatures f = TransactionRiskFeatures.newBuilder()
                .setAmount(42.00).setCurrency("USD").setTransactionType("PURCHASE")
                .setCountryCode("US").setTxCountLastHour(1).setTxCountLast24H(3)
                .setAvgAmountLast30D(60).setDistinctCountriesLast24H(1)
                .build();

        RiskModel.RiskAssessment a = model.score(f);

        assertThat(a.band()).isEqualTo("LOW");
        assertThat(a.score()).isBetween(0.0, 0.30);
    }

    @Test
    void accountTakeoverPatternScoresCritical() {
        TransactionRiskFeatures f = TransactionRiskFeatures.newBuilder()
                .setAmount(5000).setCurrency("USD").setTransactionType("WITHDRAWAL")
                .setCountryCode("RO")
                .setTxCountLastHour(12)               // high velocity
                .setAvgAmountLast30D(80)              // spend spike (62x)
                .setDistinctCountriesLast24H(4)       // geo dispersion
                .setKmFromLastTx(6000).setSecondsSinceLastTx(600) // impossible travel
                .setNewDevice(true).setFailedTxLastHour(4)
                .build();

        RiskModel.RiskAssessment a = model.score(f);

        assertThat(a.band()).isEqualTo("CRITICAL");
        assertThat(a.score()).isGreaterThan(0.80);
        assertThat(a.factors()).anyMatch(s -> s.contains("Impossible travel"));
        assertThat(a.factors()).anyMatch(s -> s.contains("velocity"));
    }

    @Test
    void scoreIsAlwaysBounded() {
        RiskModel.RiskAssessment a = model.score(TransactionRiskFeatures.getDefaultInstance());
        assertThat(a.score()).isBetween(0.0, 1.0);
        assertThat(a.modelVersion()).isEqualTo(RiskModel.MODEL_VERSION);
    }
}
