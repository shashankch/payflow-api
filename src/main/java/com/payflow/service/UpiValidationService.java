package com.payflow.service;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import com.payflow.client.UpiValidationClient;
import com.payflow.client.UpiVerificationResponse;
import com.payflow.exception.InvalidUpiException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

@Service
public class UpiValidationService {

	private static final Logger LOG = LoggerFactory.getLogger(UpiValidationService.class);

	private final UpiValidationClient upiValidationClient;
	private final boolean validationEnabled;

	public UpiValidationService(UpiValidationClient upiValidationClient, //
			@Value("${payflow.upi-validation.enabled:false}") boolean validationEnabled) {
		this.upiValidationClient = upiValidationClient;
		this.validationEnabled = validationEnabled;
	}

	public void validateUpi(String upiId) {
		if (!validationEnabled) {
			LOG.debug("External UPI validation disabled. Skipping for {}", upiId);
			return;
		}

		UpiVerificationResponse response = executeValidationWithRetry(upiId);
		if (response != null && !response.valid()) {
			LOG.warn("External UPI validation failed for UPI ID: {}", upiId);
			throw new InvalidUpiException("UPI ID is invalid or rejected by external provider: " + upiId);
		}

		LOG.info("External UPI validation successful for: {}, bank: {}", upiId,
				response != null ? response.bankName() : "UNKNOWN");
	}

	@CircuitBreaker(name = "upiValidation", fallbackMethod = "recoverFromValidationFailure")
	@Retryable(retryFor = {RestClientException.class, IOException.class}, //
			maxAttempts = 3, //
			backoff = @Backoff(delay = 500, multiplier = 2.0, random = true))
	public UpiVerificationResponse executeValidationWithRetry(String upiId) {
		LOG.debug("Calling external UPI validation service for: {}", upiId);
		return upiValidationClient.verify(upiId);
	}

	@Recover
	public UpiVerificationResponse recoverFromValidationFailure(Exception ex, String upiId) {
		String msg = "All retries failed for UPI validation of " + upiId + ": " + ex.getMessage();
		LOG.warn("{}. Proceeding with graceful fallback.", msg);
		return new UpiVerificationResponse(true, "UNKNOWN (FALLBACK)", "UNKNOWN");
	}

	public UpiVerificationResponse recoverFromValidationFailure(String upiId, Throwable t) {
		String msg = "Circuit breaker fallback for UPI: " + upiId + ": " + t.getMessage();
		LOG.warn("{}. Proceeding with graceful fallback.", msg);
		return new UpiVerificationResponse(true, "UNKNOWN (FALLBACK)", "UNKNOWN");
	}

	public boolean isValidationEnabled() {
		return validationEnabled;
	}
}
