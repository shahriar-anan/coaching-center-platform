package com.coachingcenter.api.offline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.FieldErrorDetail;

/**
 * Offline instructors, read for the routine picker (OFFLINE_BATCH_ROUTINE). Since the accounting build (27 Nov) the Master Admin keeps
 * them as payees of the Offline instructors group (accounting ledger, plan 4.3): the writes are in the accounting API, and the payee
 * keeps this row in step. The alias rules live here, so both places read them the same way.
 */
@Service
public class OfflineInstructorService {

	/** The most alternative names one instructor may have. */
	public static final int MAX_ALIASES = 20;

	/** The longest name or alias, the column's length. */
	public static final int MAX_NAME = 200;

	/**
	 * The instructors by name. {@code q} matches the name, the phone or an alias, ignoring case. Archived ones are listed only when asked.
	 */
	@Transactional(readOnly = true)
	public List<InstructorView> list(String q, boolean includeArchived) {
		List<OfflineInstructor> found = includeArchived ? instructors.findAllByOrderByNameAsc()
				: instructors.findByArchivedAtIsNullOrderByNameAsc();
		String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
		List<InstructorView> views = new ArrayList<>();
		for (OfflineInstructor instructor : found) {
			if (needle.isEmpty() || matches(instructor, needle)) {
				views.add(view(instructor));
			}
		}
		return views;
	}

	/**
	 * The alternative names of an instructor, as the user sent them: each trimmed, blanks and repeats (ignoring case) dropped, at most
	 * {@value #MAX_ALIASES} names of at most {@value #MAX_NAME} characters. Problems go to {@code problems} under the field "aliases".
	 * Null gives no names.
	 */
	public static String[] cleanAliases(Collection<String> raw, List<FieldErrorDetail> problems) {
		if (raw == null) {
			return new String[0];
		}
		Set<String> seen = new LinkedHashSet<>();
		Set<String> lower = new LinkedHashSet<>();
		for (String item : raw) {
			String text = item == null ? "" : item.trim();
			if (text.isEmpty() || !lower.add(text.toLowerCase(Locale.ROOT))) {
				continue;
			}
			if (text.length() > MAX_NAME) {
				problems.add(new FieldErrorDetail("aliases", "A name is longer than " + MAX_NAME + " characters."));
				return new String[0];
			}
			seen.add(text);
		}
		if (seen.size() > MAX_ALIASES) {
			problems.add(new FieldErrorDetail("aliases", "There can be at most " + MAX_ALIASES + " names."));
			return new String[0];
		}
		return seen.toArray(new String[0]);
	}

	InstructorView view(OfflineInstructor instructor) {
		String[] aliases = instructor.getAliases() == null ? new String[0] : instructor.getAliases();
		return new InstructorView(instructor.getId(), instructor.getName(), instructor.getPhone(), instructor.getNote(),
				List.of(aliases), instructor.getArchivedAt() != null, instructor.getVersion() == null ? 0L : instructor.getVersion());
	}

	private static boolean matches(OfflineInstructor instructor, String needle) {
		if (instructor.getName().toLowerCase(Locale.ROOT).contains(needle)) {
			return true;
		}
		if (instructor.getPhone() != null && instructor.getPhone().contains(needle)) {
			return true;
		}
		if (instructor.getAliases() != null) {
			for (String alias : instructor.getAliases()) {
				if (alias.toLowerCase(Locale.ROOT).contains(needle)) {
					return true;
				}
			}
		}
		return false;
	}

	/** One instructor as the staff and the Master Admin see them. {@code version} is what a later edit must send back. */
	public record InstructorView(UUID id, String name, String phone, String note, List<String> aliases, boolean archived,
			long version) {
	}

	private final OfflineInstructorRepository instructors;

	public OfflineInstructorService(OfflineInstructorRepository instructors) {
		this.instructors = instructors;
	}

}
