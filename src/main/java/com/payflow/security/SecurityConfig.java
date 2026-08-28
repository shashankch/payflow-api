package com.payflow.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
	private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
	private final String allowedOrigins;

	public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, //
			JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint, //
			JwtAccessDeniedHandler jwtAccessDeniedHandler, //
			@Value("${payflow.security.cors.allowed-origins:*}") String allowedOrigins) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
		this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
		this.allowedOrigins = allowedOrigins;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable);
		http.cors(cors -> cors.configurationSource(corsConfigurationSource()));
		http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		http.exceptionHandling(e -> e.authenticationEntryPoint(jwtAuthenticationEntryPoint)
				.accessDeniedHandler(jwtAccessDeniedHandler));
		http.authorizeHttpRequests(this::configureAuth);
		http.headers(h -> h.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable));
		http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	private void configureAuth(
			AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
		auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/users").permitAll();
		auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
		auth.requestMatchers("/actuator/health/**", "/actuator/info").permitAll();
		auth.requestMatchers("/h2-console/**").permitAll();
		auth.anyRequest().authenticated();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		if ("*".equals(allowedOrigins)) {
			configuration.addAllowedOriginPattern("*");
		} else {
			configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
		}
		String[] methods = {"GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"};
		configuration.setAllowedMethods(Arrays.asList(methods));
		configuration.setAllowedHeaders(List.of("*"));
		configuration.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
