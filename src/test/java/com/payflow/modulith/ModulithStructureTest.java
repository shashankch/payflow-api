package com.payflow.modulith;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import com.payflow.PayflowApiApplication;

class ModulithStructureTest {

	@Test
	@DisplayName("Should verify Spring Modulith architectural boundaries and module encapsulation")
	void verifyModulithStructure() {
		ApplicationModules modules = ApplicationModules.of(PayflowApiApplication.class);
		modules.verify();
	}
}
