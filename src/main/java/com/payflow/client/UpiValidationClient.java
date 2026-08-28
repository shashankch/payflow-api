package com.payflow.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/api/v1/upi")
public interface UpiValidationClient {

	@GetExchange("/verify/{upiId}")
	UpiVerificationResponse verify(@PathVariable("upiId") String upiId);
}
