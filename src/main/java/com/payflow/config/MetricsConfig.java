package com.payflow.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;

@Configuration
public class MetricsConfig {

	public static final String METRIC_TRANSFERS_TOTAL = "payflow.transfers.total";
	public static final String METRIC_TRANSFERS_AMOUNT = "payflow.transfers.amount";
	public static final String METRIC_TRANSFERS_DURATION = "payflow.transfers.duration";
	public static final String TAG_STATUS = "status";

	private final MeterRegistry meterRegistry;
	private final DistributionSummary transferAmountSummary;
	private final Timer transferDurationTimer;

	public MetricsConfig(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
		this.transferAmountSummary = DistributionSummary.builder(METRIC_TRANSFERS_AMOUNT) //
				.description("Distribution of transfer amounts in INR") //
				.baseUnit("INR") //
				.publishPercentiles(0.5, 0.95, 0.99) //
				.minimumExpectedValue(1.0) //
				.maximumExpectedValue(1000000.0) //
				.register(meterRegistry);

		this.transferDurationTimer = Timer.builder(METRIC_TRANSFERS_DURATION) //
				.description("Timer tracking transfer execution latency") //
				.publishPercentiles(0.5, 0.95, 0.99) //
				.minimumExpectedValue(Duration.ofMillis(1)) //
				.maximumExpectedValue(Duration.ofSeconds(10)) //
				.register(meterRegistry);
	}

	@Bean
	public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
		return new ObservedAspect(observationRegistry);
	}

	public void recordTransfer(String status, double amount, long durationMs) {
		Counter.builder(METRIC_TRANSFERS_TOTAL).description("Total number of transfer attempts by status")
				.tag(TAG_STATUS, status).register(meterRegistry).increment();

		if ("COMPLETED".equalsIgnoreCase(status) && amount > 0) {
			transferAmountSummary.record(amount);
		}

		if (durationMs >= 0) {
			transferDurationTimer.record(Duration.ofMillis(durationMs));
		}
	}

	public void incrementTransferStatus(String status) {
		Counter.builder(METRIC_TRANSFERS_TOTAL).description("Total number of transfer attempts by status")
				.tag(TAG_STATUS, status).register(meterRegistry).increment();
	}
}
