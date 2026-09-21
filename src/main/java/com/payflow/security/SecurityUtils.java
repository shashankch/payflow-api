package com.payflow.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

	private static final String ANONYMOUS_USER = "anonymousUser";

	private SecurityUtils() {
		// Private constructor for static utility class
	}

	public static String getAuthenticatedUpiId() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || ANONYMOUS_USER.equals(auth.getPrincipal())) {
			return null;
		}
		return auth.getName();
	}

	public static boolean hasRole(String role) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated()) {
			return false;
		}
		String targetRole = role.startsWith("ROLE_") ? role : "ROLE_" + role;
		return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equalsIgnoreCase(targetRole));
	}
}
