package com.payflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;

class MetricsConfigTest {

	private MeterRegistry meterRegistry;
	private MetricsConfig metricsConfig;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		metricsConfig = new MetricsConfig(meterRegistry);
	}

	@Test
	@DisplayName("Should record completed transfer metrics for counter, summary, and timer")
	void shouldRecordCompletedTransferMetrics() {
		metricsConfig.recordTransfer("COMPLETED", 2500.0, 45);

		Counter counter = meterRegistry.find(MetricsConfig.METRIC_TRANSFERS_TOTAL)
				.tag(MetricsConfig.TAG_STATUS, "COMPLETED").counter();
		assertThat(counter).isNotNull();
		assertThat(counter.count()).isEqualTo(1.0);

		DistributionSummary summary = meterRegistry.find(MetricsConfig.METRIC_TRANSFERS_AMOUNT).summary();
		assertThat(summary).isNotNull();
		assertThat(summary.count()).isEqualTo(1);
		assertThat(summary.totalAmount()).isEqualTo(2500.0);

		Timer timer = meterRegistry.find(MetricsConfig.METRIC_TRANSFERS_DURATION).timer();
		assertThat(timer).isNotNull();
		assertThat(timer.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("Should increment transfer status counter without recording amount for failure")
	void shouldIncrementTransferStatus() {
		metricsConfig.incrementTransferStatus("INSUFFICIENT_BALANCE");

		Counter counter = meterRegistry.find(MetricsConfig.METRIC_TRANSFERS_TOTAL)
				.tag(MetricsConfig.TAG_STATUS, "INSUFFICIENT_BALANCE").counter();
		assertThat(counter).isNotNull();
		assertThat(counter.count()).isEqualTo(1.0);

		DistributionSummary summary = meterRegistry.find(MetricsConfig.METRIC_TRANSFERS_AMOUNT).summary();
		assertThat(summary).isNotNull();
		assertThat(summary.count()).isEqualTo(0);
	}

	@Test
	@DisplayName("Should instantiate ObservedAspect with observation registry")
	void shouldCreateObservedAspect() {
		ObservationRegistry observationRegistry = ObservationRegistry.create();
		ObservedAspect aspect = metricsConfig.observedAspect(observationRegistry);
		assertThat(aspect).isNotNull();
	}
}
