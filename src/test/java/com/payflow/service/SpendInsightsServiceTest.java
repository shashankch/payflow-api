package com.payflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.payflow.dto.response.SpendInsightResponse;
import com.payflow.entity.Transaction;
import com.payflow.entity.TransactionStatus;
import com.payflow.entity.TransactionType;
import com.payflow.entity.User;
import com.payflow.exception.FeatureDisabledException;

@ExtendWith(MockitoExtension.class)
class SpendInsightsServiceTest {

	@Mock
	private TransactionService transactionService;

	@Mock
	private LlmInsightClient llmInsightClient;

	private SpendInsightsServiceImpl enabledService;
	private SpendInsightsServiceImpl disabledService;
	private LlmInsightClient standaloneLlmClient;

	@BeforeEach
	void setUp() {
		enabledService = new SpendInsightsServiceImpl(transactionService, llmInsightClient, true);
		disabledService = new SpendInsightsServiceImpl(transactionService, llmInsightClient, false);
		standaloneLlmClient = new LlmInsightClient(null);
	}

	private Transaction createTestTransaction(String receiverUpi) {
		User sender = User.builder().userId(1L).referenceId(UUID.randomUUID()).name("Alice").upiId("alice@payflow")
				.balance(new BigDecimal("500.00")).version(0L).build();

		User receiver = User.builder().userId(2L).referenceId(UUID.randomUUID()).name("Bob").upiId(receiverUpi)
				.balance(new BigDecimal("200.00")).version(0L).build();

		return Transaction.builder() //
				.transactionId(10L) //
				.referenceId(UUID.randomUUID()) //
				.sender(sender) //
				.receiver(receiver) //
				.senderUpiId("alice@payflow") //
				.receiverUpiId(receiverUpi) //
				.amount(new BigDecimal("450.00")) //
				.type(TransactionType.TRANSFER) //
				.status(TransactionStatus.COMPLETED) //
				.build();
	}

	@Test
	@DisplayName("generateSpendInsights should throw FeatureDisabledException when AI is disabled")
	void shouldThrowFeatureDisabledException_whenAiDisabled() {
		UUID refId = UUID.randomUUID();

		assertThatThrownBy(() -> disabledService.generateSpendInsights(refId)) //
				.isInstanceOf(FeatureDisabledException.class) //
				.hasMessageContaining("Gen-AI Spend Insights feature is disabled");
	}

	@Test
	@DisplayName("generateSpendInsights should delegate to LlmInsightClient when AI is enabled")
	void shouldDelegateToLlmClient_whenAiEnabled() {
		Transaction tx = createTestTransaction("swiggy@upi");
		UUID refId = tx.getReferenceId();

		SpendInsightResponse expected = new SpendInsightResponse( //
				refId, "FOOD_AND_DINING", tx.getAmount(), //
				"Dining expense", "Tip", 0.95, "AI_MODEL");

		when(transactionService.getTransactionByReferenceId(refId)).thenReturn(tx);
		when(llmInsightClient.generate(tx)).thenReturn(expected);

		SpendInsightResponse actual = enabledService.generateSpendInsights(refId);

		assertThat(actual).isNotNull();
		assertThat(actual.transactionReferenceId()).isEqualTo(refId);
		assertThat(actual.category()).isEqualTo("FOOD_AND_DINING");
		assertThat(actual.source()).isEqualTo("AI_MODEL");
		verify(transactionService).getTransactionByReferenceId(refId);
		verify(llmInsightClient).generate(tx);
	}

	@Test
	@DisplayName("LlmInsightClient should throw IllegalStateException if ChatClient is null in generate")
	void shouldThrowIllegalStateException_whenChatClientNull() {
		Transaction tx = createTestTransaction("store@upi");

		assertThatThrownBy(() -> standaloneLlmClient.generate(tx)) //
				.isInstanceOf(IllegalStateException.class) //
				.hasMessageContaining("ChatClient bean is not available");
	}

	@Test
	@DisplayName("ruleBasedFallback should successfully categorize FOOD_AND_DINING for food merchant")
	void shouldCategorizeFoodKeyword() {
		Transaction tx = createTestTransaction("zomato@icici");

		SpendInsightResponse res = standaloneLlmClient.ruleBasedFallback(tx, new RuntimeException("Downstream error"));

		assertThat(res.category()).isEqualTo("FOOD_AND_DINING");
		assertThat(res.source()).isEqualTo("RULE_BASED_FALLBACK");
		assertThat(res.confidenceScore()).isEqualTo(0.85);
		assertThat(res.summary()).contains("Dining or grocery expenditure");
	}

	@Test
	@DisplayName("ruleBasedFallback should successfully categorize SHOPPING for retail merchant")
	void shouldCategorizeShoppingKeyword() {
		Transaction tx = createTestTransaction("amazon@apl");

		SpendInsightResponse res = standaloneLlmClient.ruleBasedFallback(tx, new RuntimeException("Timeout"));

		assertThat(res.category()).isEqualTo("SHOPPING");
		assertThat(res.source()).isEqualTo("RULE_BASED_FALLBACK");
		assertThat(res.summary()).contains("Retail or e-commerce purchase");
	}

	@Test
	@DisplayName("ruleBasedFallback should successfully categorize TRANSPORTATION for transit merchant")
	void shouldCategorizeTransportKeyword() {
		Transaction tx = createTestTransaction("uber@paytm");

		SpendInsightResponse res = standaloneLlmClient.ruleBasedFallback(tx, new RuntimeException("Timeout"));

		assertThat(res.category()).isEqualTo("TRANSPORTATION");
		assertThat(res.source()).isEqualTo("RULE_BASED_FALLBACK");
		assertThat(res.summary()).contains("Transit or fuel expense");
	}

	@Test
	@DisplayName("ruleBasedFallback should successfully categorize UTILITIES for utility merchant")
	void shouldCategorizeUtilityKeyword() {
		Transaction tx = createTestTransaction("electricity.bill@sbipay");

		SpendInsightResponse res = standaloneLlmClient.ruleBasedFallback(tx, new RuntimeException("Timeout"));

		assertThat(res.category()).isEqualTo("UTILITIES");
		assertThat(res.source()).isEqualTo("RULE_BASED_FALLBACK");
		assertThat(res.summary()).contains("Recurring utility payment");
	}

	@Test
	@DisplayName("ruleBasedFallback should successfully categorize ENTERTAINMENT for media merchant")
	void shouldCategorizeEntertainmentKeyword() {
		Transaction tx = createTestTransaction("netflix@hdfcbank");

		SpendInsightResponse res = standaloneLlmClient.ruleBasedFallback(tx, new RuntimeException("Timeout"));

		assertThat(res.category()).isEqualTo("ENTERTAINMENT");
		assertThat(res.source()).isEqualTo("RULE_BASED_FALLBACK");
		assertThat(res.summary()).contains("Entertainment or subscription charge");
	}

	@Test
	@DisplayName("ruleBasedFallback should successfully categorize HEALTHCARE for medical merchant")
	void shouldCategorizeHealthcareKeyword() {
		Transaction tx = createTestTransaction("apollo.pharmacy@axis");

		SpendInsightResponse res = standaloneLlmClient.ruleBasedFallback(tx, new RuntimeException("Timeout"));

		assertThat(res.category()).isEqualTo("HEALTHCARE");
		assertThat(res.source()).isEqualTo("RULE_BASED_FALLBACK");
		assertThat(res.summary()).contains("Medical or healthcare expense");
	}

	@Test
	@DisplayName("ruleBasedFallback should successfully categorize INVESTMENTS for trading merchant")
	void shouldCategorizeInvestmentsKeyword() {
		Transaction tx = createTestTransaction("zerodha.funds@hdfcbank");

		SpendInsightResponse res = standaloneLlmClient.ruleBasedFallback(tx, new RuntimeException("Timeout"));

		assertThat(res.category()).isEqualTo("INVESTMENTS");
		assertThat(res.source()).isEqualTo("RULE_BASED_FALLBACK");
		assertThat(res.summary()).contains("Asset allocation transfer");
	}

	@Test
	@DisplayName("ruleBasedFallback should fallback to TRANSFER when no merchant keyword matches")
	void shouldFallbackToTransferPayments_whenNoKeywordMatches() {
		Transaction tx = createTestTransaction("friend@okaxis");

		SpendInsightResponse res = standaloneLlmClient.ruleBasedFallback(tx, new RuntimeException("Timeout"));

		assertThat(res.category()).isEqualTo("TRANSFER");
		assertThat(res.source()).isEqualTo("RULE_BASED_FALLBACK");
		assertThat(res.confidenceScore()).isEqualTo(0.80);
		assertThat(res.summary()).contains("Direct peer-to-peer transfer");
	}
}
