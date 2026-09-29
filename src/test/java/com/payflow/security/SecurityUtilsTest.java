package com.payflow.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("SecurityUtils Unit Tests")
class SecurityUtilsTest {

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("Should return null when no authentication exists in SecurityContext")
	void shouldReturnNullWhenNoAuth() {
		SecurityContextHolder.clearContext();
		assertThat(SecurityUtils.getAuthenticatedUpiId()).isNull();
		assertThat(SecurityUtils.hasRole("USER")).isFalse();
	}

	@Test
	@DisplayName("Should return null when user is anonymous or unauthenticated")
	void shouldReturnNullWhenAnonymousOrUnauthenticated() {
		UsernamePasswordAuthenticationToken unauthenticated = new UsernamePasswordAuthenticationToken("anonymousUser",
				null);
		SecurityContextHolder.getContext().setAuthentication(unauthenticated);

		assertThat(SecurityUtils.getAuthenticatedUpiId()).isNull();

		UsernamePasswordAuthenticationToken notAuth = UsernamePasswordAuthenticationToken
				.unauthenticated("user@payflow", "creds");
		SecurityContextHolder.getContext().setAuthentication(notAuth);

		assertThat(SecurityUtils.getAuthenticatedUpiId()).isNull();
		assertThat(SecurityUtils.hasRole("USER")).isFalse();
	}

	@Test
	@DisplayName("Should return authenticated upiId and check role correctly")
	void shouldReturnAuthenticatedUpiIdAndCheckRoles() {
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("alice@payflow", "creds",
				List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN")));
		SecurityContextHolder.getContext().setAuthentication(auth);

		assertThat(SecurityUtils.getAuthenticatedUpiId()).isEqualTo("alice@payflow");
		assertThat(SecurityUtils.hasRole("USER")).isTrue();
		assertThat(SecurityUtils.hasRole("ROLE_USER")).isTrue();
		assertThat(SecurityUtils.hasRole("ADMIN")).isTrue();
		assertThat(SecurityUtils.hasRole("SUPERADMIN")).isFalse();
	}

	@Test
	@DisplayName("Private constructor can be invoked via reflection for coverage")
	void testPrivateConstructor() throws Exception {
		Constructor<SecurityUtils> constructor = SecurityUtils.class.getDeclaredConstructor();
		constructor.setAccessible(true);
		SecurityUtils instance = constructor.newInstance();
		assertThat(instance).isNotNull();
	}
}
