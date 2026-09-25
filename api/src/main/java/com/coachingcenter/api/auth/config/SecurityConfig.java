package com.coachingcenter.api.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

import com.coachingcenter.api.auth.JwtAuthenticationFilter;
import com.coachingcenter.api.common.error.ApiError;
import com.coachingcenter.api.common.error.ApiErrorHttpWriter;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.RequestIds;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ApiErrorHttpWriter errors, JwtAuthenticationFilter jwt)
			throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)
			.cors(Customizer.withDefaults())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.addFilterBefore(jwt, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
			.exceptionHandling(handling -> handling
				.authenticationEntryPoint((request, response, authException) -> errors.write(response,
						HttpStatus.UNAUTHORIZED.value(),
						ApiError.of(ErrorCode.UNAUTHENTICATED, "Authentication is required.", RequestIds.current(request))))
				.accessDeniedHandler((request, response, accessDeniedException) -> errors.write(response,
						HttpStatus.FORBIDDEN.value(),
						ApiError.of(ErrorCode.ACCESS_DENIED, "Access is denied.", RequestIds.current(request)))))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
				.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/activate",
						"/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/forgot-password",
						"/api/v1/auth/reset-password", "/api/v1/auth/verify-email", "/api/v1/auth/verify-email/resend")
				.permitAll()
				.anyRequest()
				.authenticated());
		return http.build();
	}

	@Bean
	UserDetailsService userDetailsService() {
		return username -> {
			throw new UsernameNotFoundException("User not found.");
		};
	}

}
