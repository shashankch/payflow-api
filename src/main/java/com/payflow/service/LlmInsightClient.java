package com.payflow.service;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.payflow.dto.response.SpendInsightResponse;
import com.payflow.entity.Transaction;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

@Component
public class LlmInsightClient {

	private static final Logger LOG = LoggerFactory.getLogger(LlmInsightClient.class);

	private static final String[] FOOD_KEYWORDS = { //
			"food", "swiggy", "zomato", "cafe", "restaurant", //
			"dining", "starbucks", "mcdonalds", "groceries", //
			"blinkit", "zepto", "instamart", "bakery", "eats"};

	private static final String[] SHOPPING_KEYWORDS = { //
			"amazon", "flipkart", "myntra", "store", "retail", //
			"shop", "mall", "market", "fashion", "mart"};

	private static final String[] TRANSPORT_KEYWORDS = { //
			"uber", "ola", "metro", "fuel", "petrol", "diesel", //
			"transport", "cab", "taxi", "travel", "flight"};

	private static final String[] UTILITY_KEYWORDS = { //
			"electricity", "water", "gas", "bill", "broadband", //
			"wifi", "mobile", "recharge", "airtel", "jio", "power"};

	private static final String[] ENTERTAINMENT_KEYWORDS = { //
			"netflix", "spotify", "prime", "movie", "cinema", //
			"entertainment", "game", "steam", "theatre"};

	private static final String[] HEALTH_KEYWORDS = { //
			"health", "hospital", "pharmacy", "medical", "clinic", //
			"doctor", "apollo", "chemist", "wellness"};

	private static final String[] INVEST_KEYWORDS = { //
			"invest", "zerodha", "groww", "mutual", "fund", //
			"stock", "sip", "securities", "broker", "gold"};

	private final ChatClient chatClient;

	public LlmInsightClient( //
			@Autowired(required = false) ChatClient chatClient) {
		this.chatClient = chatClient;
	}

	@CircuitBreaker(name = "aiCircuitBreaker", fallbackMethod = "ruleBasedFallback")
	public SpendInsightResponse generate(Transaction transaction) {
		if (chatClient == null) {
			String err = "ChatClient bean is not available in application context";
			throw new IllegalStateException(err);
		}

		String prompt = buildPrompt(transaction);
		LOG.debug("Submitting tx {} to LLM for insights", transaction.getReferenceId());

		var call = chatClient.prompt().user(prompt).call();
		SpendInsightResponse aiResponse = call.entity(SpendInsightResponse.class);

		if (aiResponse == null) {
			throw new IllegalStateException("LLM returned empty or null spend insight response");
		}

		String category = aiResponse.category() != null ? aiResponse.category() : "OTHER";
		String defSummary = "Expenditure of INR " + transaction.getAmount() + " to " //
				+ transaction.getReceiverUpiId();
		String defTip = "Track your recurring expenses regularly to stay on top of your financial health.";
		String summary = aiResponse.summary() != null ? aiResponse.summary() : defSummary;
		String tip = aiResponse.budgetingTip() != null ? aiResponse.budgetingTip() : defTip;
		Double confidence = aiResponse.confidenceScore() != null ? aiResponse.confidenceScore() : 0.90;

		return new SpendInsightResponse( //
				transaction.getReferenceId(), //
				category, //
				transaction.getAmount(), //
				summary, //
				tip, //
				confidence, //
				"AI_MODEL");
	}

	public SpendInsightResponse ruleBasedFallback(Transaction tx, Throwable t) {
		LOG.warn("AI fallback for tx {}: {}", tx.getReferenceId(), t.getMessage());
		return categorizeWithRules(tx);
	}

	public SpendInsightResponse categorizeWithRules(Transaction transaction) {
		String rx = transaction.getReceiverUpiId();
		String receiver = rx != null ? rx.toLowerCase(Locale.ROOT) : "";

		String category;
		String summary;
		String tip;
		double confidence = 0.85;

		if (matchesAny(receiver, FOOD_KEYWORDS)) {
			category = "FOOD_AND_DINING";
			summary = "Dining or grocery expenditure with " + rx + ".";
			tip = "Set a dedicated dining out ceiling to optimize discretionary spending.";
		} else if (matchesAny(receiver, SHOPPING_KEYWORDS)) {
			category = "SHOPPING";
			summary = "Retail or e-commerce purchase paid to " + rx + ".";
			tip = "Use a 48-hour cooling-off rule before non-essential purchases to reduce impulse buys.";
		} else if (matchesAny(receiver, TRANSPORT_KEYWORDS)) {
			category = "TRANSPORTATION";
			summary = "Transit or fuel expense paid to " + rx + ".";
			tip = "Explore monthly transit cards or shared ride options to trim commuting costs.";
		} else if (matchesAny(receiver, UTILITY_KEYWORDS)) {
			category = "UTILITIES";
			summary = "Recurring utility payment to " + rx + ".";
			tip = "Schedule automated utility payments to prevent late fees and monitor spikes.";
		} else if (matchesAny(receiver, ENTERTAINMENT_KEYWORDS)) {
			category = "ENTERTAINMENT";
			summary = "Entertainment or subscription charge from " + rx + ".";
			tip = "Audit recurring subscriptions and pause those used infrequently.";
		} else if (matchesAny(receiver, HEALTH_KEYWORDS)) {
			category = "HEALTHCARE";
			summary = "Medical or healthcare expense paid to " + rx + ".";
			tip = "Maintain an emergency medical reserve to cushion unexpected wellness expenditures.";
		} else if (matchesAny(receiver, INVEST_KEYWORDS)) {
			category = "INVESTMENTS";
			summary = "Asset allocation transfer to " + rx + ".";
			tip = "Systematic automated monthly investments compound significantly over long horizons.";
		} else {
			category = "TRANSFER";
			summary = "Direct peer-to-peer transfer to " + rx + ".";
			tip = "Track uncategorized transfers regularly to align with savings targets.";
			confidence = 0.80;
		}

		return new SpendInsightResponse( //
				transaction.getReferenceId(), //
				category, //
				transaction.getAmount(), //
				summary, //
				tip, //
				confidence, //
				"RULE_BASED_FALLBACK");
	}

	private boolean matchesAny(String text, String... keywords) {
		for (String keyword : keywords) {
			if (text.contains(keyword)) {
				return true;
			}
		}
		return false;
	}

	private String buildPrompt(Transaction tx) {
		return "Analyze peer-to-peer transaction:\n" //
				+ "- Ref: " + tx.getReferenceId() + "\n" //
				+ "- Sender: " + tx.getSenderUpiId() + "\n" //
				+ "- Receiver: " + tx.getReceiverUpiId() + "\n" //
				+ "- Amount: INR " + tx.getAmount() + "\n" //
				+ "- Time: " + tx.getCreatedAt() + "\n\n" //
				+ "Classify into: FOOD_AND_DINING, SHOPPING, UTILITIES, " //
				+ "TRANSPORTATION, ENTERTAINMENT, HEALTHCARE, INVESTMENTS, TRANSFER, OTHER.\n" //
				+ "Provide a concise spend summary and an actionable budgeting tip.";
	}
}
