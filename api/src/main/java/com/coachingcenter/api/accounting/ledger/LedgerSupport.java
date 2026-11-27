package com.coachingcenter.api.accounting.ledger;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

import com.coachingcenter.api.accounting.AccRefusals;
import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;

/**
 * What the ledger controllers and services share: the ACCOUNTING permission (every route; a System Admin gets 403), the signed-in user,
 * the reading of text and choice fields, the version check and the order lists. Field problems are collected and raised together as one
 * 400, as the rest of the API does.
 */
final class LedgerSupport {

	private LedgerSupport() {
	}

	/** The ACCOUNTING permission, or 403. Every ledger route calls it first. */
	static void require(PermissionChecker permissions) {
		if (!permissions.has(Permissions.ACCOUNTING)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

	static UUID actorId() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor.userId();
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

	/** A text field, trimmed. Blank or absent gives null; longer than the column allows is a problem for that field. */
	static String text(String raw, int max, String field, List<FieldErrorDetail> problems) {
		if (raw == null) {
			return null;
		}
		String value = raw.trim();
		if (value.isEmpty()) {
			return null;
		}
		if (value.length() > max) {
			problems.add(new FieldErrorDetail(field, "This is longer than " + max + " characters."));
			return null;
		}
		return value;
	}

	/** A text field that must have a value. */
	static String required(String raw, int max, String field, List<FieldErrorDetail> problems) {
		if (raw == null || raw.isBlank()) {
			problems.add(new FieldErrorDetail(field, "This field is required."));
			return null;
		}
		return text(raw, max, field, problems);
	}

	/** One of the allowed codes, trimmed; anything else is a problem for that field. */
	static String choice(String raw, Set<String> allowed, String field, List<FieldErrorDetail> problems) {
		if (raw == null || !allowed.contains(raw.trim())) {
			problems.add(new FieldErrorDetail(field, "Choose one of the options."));
			return null;
		}
		return raw.trim();
	}

	/** Raises every collected field problem at once (400). Does nothing when there are none. */
	static void check(List<FieldErrorDetail> problems) {
		if (!problems.isEmpty()) {
			throw AccRefusals.validation(problems);
		}
	}

	/** A write of an existing row must send the version it read. Missing is a field error; a stale one is 409. */
	static void checkVersion(Long sent, long current) {
		if (sent == null) {
			throw AccRefusals.validation("version", "Send the version you read.");
		}
		if (sent != current) {
			throw AccRefusals.stale();
		}
	}

	/** An order list must name every live row exactly once (plan 4.1, 4.2). Otherwise a field error on "ids". */
	static void checkOrder(List<UUID> sent, Collection<UUID> live) {
		if (sent == null || sent.size() != live.size() || !new HashSet<>(sent).equals(new HashSet<>(live))) {
			throw AccRefusals.validation("ids", "List every live row once, in the new order.");
		}
	}

}
