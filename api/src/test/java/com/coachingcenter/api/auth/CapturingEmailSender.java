package com.coachingcenter.api.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class CapturingEmailSender implements EmailSender {

	private final ConcurrentHashMap<String, List<String>> verifications = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<String, List<String>> resets = new ConcurrentHashMap<>();

	@Override
	public void sendEmailVerification(String to, String code) {
		verifications.computeIfAbsent(to, ignored -> new ArrayList<>()).add(code);
	}

	@Override
	public void sendPasswordReset(String to, String code) {
		resets.computeIfAbsent(to, ignored -> new ArrayList<>()).add(code);
	}

	public String latestVerification(String email) {
		return last(verifications.get(email));
	}

	public String latestReset(String email) {
		return last(resets.get(email));
	}

	private static String last(List<String> codes) {
		if (codes == null || codes.isEmpty()) {
			return null;
		}
		return codes.get(codes.size() - 1);
	}

}
