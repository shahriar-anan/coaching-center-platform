package com.coachingcenter.api.auth;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.users.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String ACTIVE = "ACTIVE";

	private final JwtService jwt;

	private final UserRepository users;

	public JwtAuthenticationFilter(JwtService jwt, UserRepository users) {
		this.jwt = jwt;
		this.users = users;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith("Bearer ")) {
			CurrentActor actor = jwt.parse(header.substring("Bearer ".length()));
			if (actor != null && isActive(actor.userId())) {
				UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(actor, null,
						List.of());
				SecurityContextHolder.getContext().setAuthentication(authentication);
			}
		}
		filterChain.doFilter(request, response);
	}

	/**
	 * Read on every request, so a suspended or deleted account loses access at once, not when its token expires.
	 */
	private boolean isActive(UUID userId) {
		return users.findByIdAndDeletedAtIsNull(userId).map(user -> ACTIVE.equals(user.getStatus())).orElse(false);
	}

}
