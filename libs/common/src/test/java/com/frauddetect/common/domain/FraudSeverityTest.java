package com.frauddetect.common.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class FraudSeverityTest {

    @ParameterizedTest
    @CsvSource({
            "0,LOW", "29,LOW",
            "30,MEDIUM", "59,MEDIUM",
            "60,HIGH", "79,HIGH",
            "80,CRITICAL", "100,CRITICAL"
    })
    void mapsScoreToBand(int score, FraudSeverity expected) {
        assertThat(FraudSeverity.fromScore(score)).isEqualTo(expected);
    }

    @Test
    void clampsOutOfRangeScores() {
        assertThat(FraudSeverity.fromScore(-50)).isEqualTo(FraudSeverity.LOW);
        assertThat(FraudSeverity.fromScore(9999)).isEqualTo(FraudSeverity.CRITICAL);
    }

    @Test
    void onlyHighAndCriticalBlock() {
        assertThat(FraudSeverity.LOW.requiresBlocking()).isFalse();
        assertThat(FraudSeverity.MEDIUM.requiresBlocking()).isFalse();
        assertThat(FraudSeverity.HIGH.requiresBlocking()).isTrue();
        assertThat(FraudSeverity.CRITICAL.requiresBlocking()).isTrue();
    }
}
