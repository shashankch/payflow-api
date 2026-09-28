package com.payflow.service;

import java.util.UUID;

import com.payflow.dto.response.SpendInsightResponse;

public interface SpendInsightsService {

	SpendInsightResponse generateSpendInsights(UUID transactionReferenceId);
}
