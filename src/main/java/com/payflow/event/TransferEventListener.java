package com.payflow.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class TransferEventListener {

	private static final Logger LOG = LoggerFactory.getLogger(TransferEventListener.class);

	@ApplicationModuleListener
	public void onTransferCompleted(TransferCompletedEvent event) {
		LOG.info("TransferCompletedEvent: refId={}, status={}", event.referenceId(), event.status());
	}
}
