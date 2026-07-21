package com.ayush.kite.orders;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GttPricingTest {

    @Test
    void roundToTick_roundsToNearestFiveCents() {
        assertThat(GttPricing.roundToTick(100.12)).isEqualTo(100.10);
        assertThat(GttPricing.roundToTick(100.13)).isEqualTo(100.15);
    }

    @Test
    void targetPrice_addsPercentageAndRoundsToTick() {
        assertThat(GttPricing.targetPrice(1000.0, 3.0)).isEqualTo(1030.0);
    }

    @Test
    void slPrice_subtractsPercentageAndRoundsToTick() {
        assertThat(GttPricing.slPrice(1000.0, 1.5)).isEqualTo(985.0);
    }
}
