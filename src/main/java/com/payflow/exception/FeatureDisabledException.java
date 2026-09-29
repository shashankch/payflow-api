package com.payflow.exception;

public class FeatureDisabledException extends RuntimeException {

	public FeatureDisabledException(String message) {
		super(message);
	}
}
