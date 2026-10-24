package com.coachingcenter.api.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class CapturingEmailSender implements EmailSender {

	private final ConcurrentHashMap<String, List<String>> verifications = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<String, List<String>> resets = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<String, List<String>> activations = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<String, List<String>> passwordChanges = new ConcurrentHashMap<>();

	@Override
	public void sendEmailVerification(String to, String code) {
		verifications.computeIfAbsent(to, ignored -> new ArrayList<>()).add(code);
	}

	@Override
	public void sendPasswordReset(String to, String code) {
		resets.computeIfAbsent(to, ignored -> new ArrayList<>()).add(code);
	}

	@Override
	public void sendPasswordChanged(String to) {
		passwordChanges.computeIfAbsent(to, ignored -> new ArrayList<>()).add("changed");
	}

	@Override
	public void sendActivation(String to, String code) {
		activations.computeIfAbsent(to, ignored -> new ArrayList<>()).add(code);
	}

	public String latestVerification(String email) {
		return last(verifications.get(email));
	}

	public String latestReset(String email) {
		return last(resets.get(email));
	}

	public String latestActivation(String email) {
		return last(activations.get(email));
	}

	public int passwordChangedCount(String email) {
		List<String> notes = passwordChanges.get(email);
		return notes == null ? 0 : notes.size();
	}

	private static String last(List<String> codes) {
		if (codes == null || codes.isEmpty()) {
			return null;
		}
		return codes.get(codes.size() - 1);
	}

}
