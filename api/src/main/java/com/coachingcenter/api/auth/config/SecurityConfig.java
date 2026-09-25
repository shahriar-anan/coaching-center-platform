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

import com.coachingcenter.api.common.error.ApiError;
import com.coachingcenter.api.common.error.ApiErrorHttpWriter;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.RequestIds;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ApiErrorHttpWriter errors) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)
			.cors(Customizer.withDefaults())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.exceptionHandling(handling -> handling
				.authenticationEntryPoint((request, response, authException) -> errors.write(response,
						HttpStatus.UNAUTHORIZED.value(),
						ApiError.of(ErrorCode.UNAUTHENTICATED, "Authentication is required.", RequestIds.current(request))))
				.accessDeniedHandler((request, response, accessDeniedException) -> errors.write(response,
						HttpStatus.FORBIDDEN.value(),
						ApiError.of(ErrorCode.ACCESS_DENIED, "Access is denied.", RequestIds.current(request)))))
			.authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
		return http.build();
	}

	@Bean
	UserDetailsService userDetailsService() {
		return username -> {
			throw new UsernameNotFoundException("User not found.");
		};
	}

}
