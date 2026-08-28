package com.payflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import com.payflow.client.UpiValidationClient;
import com.payflow.client.UpiVerificationResponse;
import com.payflow.exception.InvalidUpiException;

@ExtendWith(MockitoExtension.class)
class UpiValidationServiceTest {

	@Mock
	private UpiValidationClient upiValidationClient;

	@Test
	@DisplayName("Should skip validation when feature flag is disabled")
	void shouldSkipValidation_whenValidationDisabled() {
		UpiValidationService service = new UpiValidationService(upiValidationClient, false);

		service.validateUpi("alice@payflow");

		verifyNoInteractions(upiValidationClient);
		assertThat(service.isValidationEnabled()).isFalse();
	}

	@Test
	@DisplayName("Should pass validation when external provider returns valid=true")
	void shouldPassValidation_whenUpiIsValid() {
		UpiValidationService service = new UpiValidationService(upiValidationClient, true);
		when(upiValidationClient.verify("alice@payflow"))
				.thenReturn(new UpiVerificationResponse(true, "HDFC Bank", "Alice Johnson"));

		service.validateUpi("alice@payflow");

		verify(upiValidationClient).verify("alice@payflow");
		assertThat(service.isValidationEnabled()).isTrue();
	}

	@Test
	@DisplayName("Should throw InvalidUpiException when external provider returns valid=false")
	void shouldThrowInvalidUpiException_whenUpiIsInvalid() {
		UpiValidationService service = new UpiValidationService(upiValidationClient, true);
		when(upiValidationClient.verify("invalid@badupi")).thenReturn(new UpiVerificationResponse(false, null, null));

		assertThatThrownBy(() -> service.validateUpi("invalid@badupi")).isInstanceOf(InvalidUpiException.class)
				.hasMessageContaining("invalid@badupi");

		verify(upiValidationClient).verify("invalid@badupi");
	}

	@Test
	@DisplayName("Should execute validation directly through executeValidationWithRetry")
	void shouldExecuteValidation_whenCallingExecuteValidationWithRetry() {
		UpiValidationService service = new UpiValidationService(upiValidationClient, true);
		when(upiValidationClient.verify("alice@payflow"))
				.thenReturn(new UpiVerificationResponse(true, "SBI", "Alice Johnson"));

		UpiVerificationResponse response = service.executeValidationWithRetry("alice@payflow");

		assertThat(response).isNotNull();
		assertThat(response.valid()).isTrue();
		assertThat(response.bankName()).isEqualTo("SBI");
	}

	@Test
	@DisplayName("Should recover gracefully and return fallback response when all retries fail")
	void shouldRecoverGracefully_whenAllRetriesFail() {
		UpiValidationService service = new UpiValidationService(upiValidationClient, true);
		RestClientException error = new RestClientException("Connection timed out to UPI provider");

		UpiVerificationResponse fallback = service.recoverFromValidationFailure(error, "alice@payflow");

		assertThat(fallback).isNotNull();
		assertThat(fallback.valid()).isTrue();
		assertThat(fallback.bankName()).contains("FALLBACK");
	}
}
