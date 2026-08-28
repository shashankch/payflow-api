package com.payflow.client;

public record UpiVerificationResponse(boolean valid, String bankName, String accountHolderName) {
}
