package com.payflow.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

class UpiValidationClientTest {

	private UpiValidationClient client;
	private MockRestServiceServer mockServer;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:9090");
		mockServer = MockRestServiceServer.bindTo(builder).build();

		RestClient restClient = builder.build();
		RestClientAdapter adapter = RestClientAdapter.create(restClient);
		HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();
		client = factory.createClient(UpiValidationClient.class);
	}

	@Test
	@DisplayName("Should parse valid UPI response from external service")
	void shouldVerifyValidUpi() {
		mockServer.expect(requestTo("http://localhost:9090/api/v1/upi/verify/priya%40payflow"))
				.andExpect(method(HttpMethod.GET)).andRespond(
						withSuccess("{\"valid\":true,\"bankName\":\"HDFC Bank\",\"accountHolderName\":\"Priya Patel\"}",
								MediaType.APPLICATION_JSON));

		UpiVerificationResponse response = client.verify("priya@payflow");

		mockServer.verify();
		assertThat(response).isNotNull();
		assertThat(response.valid()).isTrue();
		assertThat(response.bankName()).isEqualTo("HDFC Bank");
		assertThat(response.accountHolderName()).isEqualTo("Priya Patel");
	}

	@Test
	@DisplayName("Should parse invalid UPI response from external service")
	void shouldVerifyInvalidUpi() {
		mockServer.expect(requestTo("http://localhost:9090/api/v1/upi/verify/invalid%40badupi"))
				.andExpect(method(HttpMethod.GET)).andRespond(withSuccess(
						"{\"valid\":false,\"bankName\":null,\"accountHolderName\":null}", MediaType.APPLICATION_JSON));

		UpiVerificationResponse response = client.verify("invalid@badupi");

		mockServer.verify();
		assertThat(response).isNotNull();
		assertThat(response.valid()).isFalse();
		assertThat(response.bankName()).isNull();
	}
}
