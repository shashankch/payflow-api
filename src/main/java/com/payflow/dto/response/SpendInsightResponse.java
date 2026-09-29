package com.payflow.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Financial categorization and actionable budgeting insights for a transaction")
public record SpendInsightResponse(@Schema(description = "Transaction UUID reference identifier") //
@JsonProperty("transactionReferenceId") //
UUID transactionReferenceId,

		@Schema(description = "High-level spending category") //
		@JsonProperty("category") //
		String category,

		@Schema(description = "Transaction monetary amount") //
		@JsonProperty("amount") //
		BigDecimal amount,

		@Schema(description = "Concise summary explaining the expenditure context") //
		@JsonProperty("summary") //
		String summary,

		@Schema(description = "Contextual budgeting advice tailored to the category and amount") //
		@JsonProperty("budgetingTip") //
		String budgetingTip,

		@Schema(description = "Confidence score of the categorization (0.0 to 1.0)") //
		@JsonProperty("confidenceScore") //
		Double confidenceScore,

		@Schema(description = "Source of the insight generation", allowableValues = {
				"AI_MODEL", "RULE_BASED_FALLBACK"}) //
		@JsonProperty("source") //
		String source) {
}
