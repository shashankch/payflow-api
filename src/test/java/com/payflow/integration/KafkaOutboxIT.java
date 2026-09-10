package com.payflow.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import com.payflow.AbstractIntegrationTest;
import com.payflow.dto.request.CreateUserRequest;
import com.payflow.dto.request.TransferMoneyRequest;
import com.payflow.dto.response.TransactionResponse;
import com.payflow.dto.response.UserResponse;
import com.payflow.entity.TransactionStatus;
import com.payflow.filter.IdempotencyFilter;

@ActiveProfiles({"test", "kafka"})
@TestPropertySource(properties = {"spring.modulith.events.externalization.enabled=true"})
@Testcontainers(disabledWithoutDocker = true)
class KafkaOutboxIT extends AbstractIntegrationTest {

	@Container
	@ServiceConnection
	static final KafkaContainer KAFKA_CONTAINER = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("Should externalize TransferCompletedEvent to Kafka payflow.transfers topic with senderUpi key")
	void shouldExternalizeEventToKafka_whenTransferCompletes() throws Exception {
		// 1. Setup Sender & Receiver
		String senderUpi = "kafka.sender@payflow";
		String receiverUpi = "kafka.receiver@payflow";

		CreateUserRequest senderReq = new CreateUserRequest();
		senderReq.setName("Kafka Sender");
		senderReq.setUpiId(senderUpi);
		senderReq.setPhoneNumber("9555333333");
		senderReq.setBalance(new BigDecimal("1500.0000"));
		restTemplate.postForEntity("/api/v1/users", senderReq, UserResponse.class);

		CreateUserRequest receiverReq = new CreateUserRequest();
		receiverReq.setName("Kafka Receiver");
		receiverReq.setUpiId(receiverUpi);
		receiverReq.setPhoneNumber("9555444444");
		receiverReq.setBalance(new BigDecimal("800.0000"));
		restTemplate.postForEntity("/api/v1/users", receiverReq, UserResponse.class);

		// 2. Setup Kafka Test Consumer subscribed to payflow.transfers
		Properties consumerProps = new Properties();
		consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_CONTAINER.getBootstrapServers());
		consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "kafka-outbox-test-group-" + UUID.randomUUID());
		consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

		try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
			consumer.subscribe(Collections.singletonList("payflow.transfers"));

			// 3. Execute Transfer with auth headers and idempotency key
			TransferMoneyRequest txReq = new TransferMoneyRequest();
			txReq.setSenderUpiId(senderUpi);
			txReq.setReceiverUpiId(receiverUpi);
			txReq.setAmount(new BigDecimal("300.0000"));
			txReq.setNote("Kafka Outbox Stream Test");

			HttpHeaders headers = authHeaders(senderUpi);
			headers.set(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, "kafka-test-key-" + UUID.randomUUID());
			HttpEntity<TransferMoneyRequest> txEntity = new HttpEntity<>(txReq, headers);

			ResponseEntity<TransactionResponse> txRes = restTemplate.exchange("/api/v1/transactions", HttpMethod.POST,
					txEntity, TransactionResponse.class);
			assertThat(txRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
			assertThat(txRes.getBody()).isNotNull();
			assertThat(txRes.getBody().status()).isEqualTo(TransactionStatus.COMPLETED);

			UUID txReferenceId = txRes.getBody().referenceId();

			// 4. Poll Kafka for the externalized event
			ConsumerRecord<String, String> matchedRecord = null;
			long deadline = System.currentTimeMillis() + 15000;
			while (System.currentTimeMillis() < deadline && matchedRecord == null) {
				ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
				for (ConsumerRecord<String, String> record : records) {
					if (record.value() != null && record.value().contains(txReferenceId.toString())) {
						matchedRecord = record;
						break;
					}
				}
			}

			assertThat(matchedRecord)
					.as("Spring Modulith must externalize TransferCompletedEvent to payflow.transfers topic")
					.isNotNull();
			assertThat(matchedRecord.key()).as("Kafka message key must match senderUpi for partition ordering")
					.isEqualTo(senderUpi);
			assertThat(matchedRecord.value()).contains("COMPLETED").contains(senderUpi).contains(receiverUpi);

			// 5. Verify transactional outbox event_publication registry also recorded the
			// event
			Integer eventCount = jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM event_publication WHERE serialized_event LIKE ?", Integer.class,
					"%" + txReferenceId.toString() + "%");
			assertThat(eventCount).isNotNull().isGreaterThanOrEqualTo(1);
		}
	}
}
