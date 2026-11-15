package com.coachingcenter.api.offline;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.coachingcenter.api.auth.PhoneNumbers;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.users.User;
import com.coachingcenter.api.users.UserRepository;

/**
 * Finds the account of an applicant who had none when applying (public-admission plan, Q3): by the form's email first, then by its
 * phone. A match means the person already has an account, so the batch is joined to it, not made again.
 */
@Component
public class OfflineAccountMatcher {

	private final UserRepository users;

	private final JdbcTemplate jdbc;

	public OfflineAccountMatcher(UserRepository users, JdbcTemplate jdbc) {
		this.users = users;
		this.jdbc = jdbc;
	}

	/**
	 * The live account with this email, the only thing an applicant is matched by (13 Nov decision D3). Empty when the email is not used.
	 */
	public Optional<User> find(String email) {
		if (email == null || email.isBlank()) {
			return Optional.empty();
		}
		return users.findByEmailAndDeletedAtIsNull(email.trim().toLowerCase(java.util.Locale.ROOT));
	}

	/**
	 * The live account that already uses this mobile number. It is never joined to an application; staff are warned, and onboarding is
	 * refused until the form is fixed, because a new account cannot take a number that is in use.
	 */
	public Optional<User> phoneOwner(String mobile) {
		if (mobile == null || mobile.isBlank()) {
			return Optional.empty();
		}
		try {
			return users.findByPhoneAndDeletedAtIsNull(PhoneNumbers.normalize(mobile));
		}
		catch (ApiException exception) {
			return Optional.empty();
		}
	}

	/** The name on the account's profile (a student's or a staff member's), or null when it has none. */
	public String nameOf(UUID userId) {
		return jdbc.queryForList("""
				select coalesce(sp.full_name, ap.full_name) from users u
				left join student_profiles sp on sp.user_id = u.id
				left join admin_profiles ap on ap.user_id = u.id
				where u.id = ?
				""", String.class, userId).stream().findFirst().orElse(null);
	}

}
