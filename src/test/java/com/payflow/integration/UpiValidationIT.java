package com.payflow.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClientException;

import com.payflow.AbstractIntegrationTest;
import com.payflow.client.UpiValidationClient;
import com.payflow.client.UpiVerificationResponse;
import com.payflow.dto.request.CreateUserRequest;
import com.payflow.dto.response.UserResponse;

@TestPropertySource(properties = "payflow.upi-validation.enabled=true")
class UpiValidationIT extends AbstractIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@MockitoBean
	private UpiValidationClient upiValidationClient;

	@Test
	@DisplayName("Should succeed user registration when external UPI validation passes")
	void shouldRegisterUser_whenExternalValidationPasses() {
		String upiId = "external.valid@payflow";
		when(upiValidationClient.verify(upiId))
				.thenReturn(new UpiVerificationResponse(true, "ICICI Bank", "External Valid"));

		CreateUserRequest request = new CreateUserRequest("External Valid User", upiId, "9888777661",
				new BigDecimal("1500.0000"));

		ResponseEntity<UserResponse> response = restTemplate.postForEntity("/api/v1/users", request,
				UserResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().upiId()).isEqualTo(upiId);
		verify(upiValidationClient).verify(upiId);
	}

	@Test
	@DisplayName("Should reject user registration with 422 when external UPI validation returns valid=false")
	void shouldRejectRegistration_whenExternalValidationFails() {
		String upiId = "external.invalid@badupi";
		when(upiValidationClient.verify(upiId)).thenReturn(new UpiVerificationResponse(false, null, null));

		CreateUserRequest request = new CreateUserRequest("Bad UPI User", upiId, "9888777662",
				new BigDecimal("500.0000"));

		ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/users", request, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(response.getBody()).contains("invalid-upi-id");
		verify(upiValidationClient).verify(upiId);
	}

	@Test
	@DisplayName("Should gracefully proceed with registration when external UPI service is down")
	void shouldProceedRegistration_whenExternalServiceDown() {
		String upiId = "external.down@payflow";
		when(upiValidationClient.verify(upiId)).thenThrow(new RestClientException("Connection refused to 9090"));

		CreateUserRequest request = new CreateUserRequest("Fallback User", upiId, "9888777663",
				new BigDecimal("800.0000"));

		ResponseEntity<UserResponse> response = restTemplate.postForEntity("/api/v1/users", request,
				UserResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().upiId()).isEqualTo(upiId);
	}
}
