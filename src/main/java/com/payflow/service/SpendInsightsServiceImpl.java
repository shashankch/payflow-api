package com.payflow.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.payflow.dto.response.SpendInsightResponse;
import com.payflow.entity.Transaction;
import com.payflow.exception.FeatureDisabledException;

@Service
public class SpendInsightsServiceImpl implements SpendInsightsService {

	private static final Logger LOG = LoggerFactory.getLogger(SpendInsightsServiceImpl.class);

	private final TransactionService transactionService;
	private final LlmInsightClient llmInsightClient;
	private final boolean aiEnabled;

	public SpendInsightsServiceImpl(TransactionService transactionService, LlmInsightClient llmInsightClient,
			@Value("${payflow.ai.enabled:false}") boolean aiEnabled) {
		this.transactionService = transactionService;
		this.llmInsightClient = llmInsightClient;
		this.aiEnabled = aiEnabled;
	}

	@Override
	public SpendInsightResponse generateSpendInsights(UUID transactionReferenceId) {
		if (!aiEnabled) {
			LOG.info("Gen-AI spend insights requested for {} but feature flag payflow.ai.enabled is false",
					transactionReferenceId);
			String msg = "Gen-AI Spend Insights feature is disabled. "
					+ "Set 'payflow.ai.enabled: true' in application configuration to enable.";
			throw new FeatureDisabledException(msg);
		}

		Transaction transaction = transactionService.getTransactionByReferenceId(transactionReferenceId);
		LOG.info("Generating spend insights for transaction reference {}", transactionReferenceId);
		return llmInsightClient.generate(transaction);
	}
}
