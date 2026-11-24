package com.coachingcenter.api.offline;

import static com.coachingcenter.api.offline.OfflineSupport.notFound;
import static com.coachingcenter.api.offline.OfflineSupport.state;
import static com.coachingcenter.api.offline.OfflineSupport.validation;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;

import tools.jackson.databind.JsonNode;

/**
 * Offline instructors (plan 6 and F5): the names kept for the record. Staff and the Master Admin create and rename them; only the Master
 * Admin archives one, and an instructor with a live class cannot be archived. The phone is staff only, which these routes already are.
 */
@Service
public class OfflineInstructorService {

	public static final String ENTITY = "OFFLINE_INSTRUCTOR";

	public static final String CREATED = "OFFLINE_INSTRUCTOR_CREATED";

	public static final String UPDATED = "OFFLINE_INSTRUCTOR_UPDATED";

	public static final String ARCHIVED = "OFFLINE_INSTRUCTOR_ARCHIVED";

	private static final int MAX_NAME = 200;

	private static final int MAX_PHONE = 20;

	private static final int MAX_NOTE = 500;

	private static final int MAX_ALIASES = 20;

	private final OfflineInstructorRepository instructors;

	private final OfflineClassRepository classes;

	private final AuditRecorder audit;

	private final Clock clock;

	public OfflineInstructorService(OfflineInstructorRepository instructors, OfflineClassRepository classes, AuditRecorder audit,
			Clock clock) {
		this.instructors = instructors;
		this.classes = classes;
		this.audit = audit;
		this.clock = clock;
	}

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

	/** Creates a live instructor. A name already used by a live instructor is refused with a field error, ignoring case. */
	@Transactional
	public InstructorView create(UUID actorId, JsonNode body) {
		List<FieldErrorDetail> problems = new ArrayList<>();
		String name = name(body, problems);
		String phone = optionalText(body, "phone", MAX_PHONE, problems);
		String note = optionalText(body, "note", MAX_NOTE, problems);
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		if (instructors.existsByNameIgnoreCaseAndArchivedAtIsNull(name)) {
			throw validation(List.of(duplicate()));
		}
		Instant now = clock.instant();
		OfflineInstructor instructor = new OfflineInstructor();
		instructor.setId(UUID.randomUUID());
		instructor.setName(name);
		instructor.setPhone(phone);
		instructor.setNote(note);
		instructor.setAliases(new String[0]);
		instructor.setCreatedBy(actorId);
		instructor.setCreatedAt(now);
		instructor.setUpdatedAt(now);
		instructors.saveAndFlush(instructor);
		audit.record(CREATED, ENTITY, instructor.getId());
		return view(instructor);
	}

	/**
	 * Changes the fields the body names: a field left out is kept, a phone or note sent empty is cleared. A {@code version} the caller
	 * read and that no longer matches is refused (409), so an older screen cannot overwrite a newer edit.
	 */
	@Transactional
	public InstructorView update(UUID id, JsonNode body) {
		OfflineInstructor instructor = find(id);
		if (instructor.getArchivedAt() != null) {
			throw state("This instructor is archived, so they cannot be changed.");
		}
		if (OfflineSupport.has(body, "version") && body.get("version").asLong() != instructor.getVersion()) {
			throw stale();
		}
		List<FieldErrorDetail> problems = new ArrayList<>();
		String name = instructor.getName();
		if (body != null && body.has("name")) {
			name = name(body, problems);
		}
		String phone = instructor.getPhone();
		if (body != null && body.has("phone")) {
			phone = optionalText(body, "phone", MAX_PHONE, problems);
		}
		String note = instructor.getNote();
		if (body != null && body.has("note")) {
			note = optionalText(body, "note", MAX_NOTE, problems);
		}
		String[] aliases = instructor.getAliases();
		if (body != null && body.has("aliases")) {
			aliases = aliases(body.get("aliases"), problems);
		}
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		if (!name.equals(instructor.getName()) && instructors.existsByNameIgnoreCaseAndArchivedAtIsNullAndIdNot(name, id)) {
			throw validation(List.of(duplicate()));
		}
		instructor.setName(name);
		instructor.setPhone(phone);
		instructor.setNote(note);
		instructor.setAliases(aliases);
		instructor.setUpdatedAt(clock.instant());
		instructors.saveAndFlush(instructor);
		audit.record(UPDATED, ENTITY, id);
		return view(instructor);
	}

	/**
	 * Archives an instructor: they drop out of the live list and their name is free again. Refused while any class that is not cancelled
	 * still names them, and the message says how many. The classes and payments stay as they are for the record.
	 */
	@Transactional
	public InstructorView archive(UUID id) {
		OfflineInstructor instructor = find(id);
		if (instructor.getArchivedAt() != null) {
			return view(instructor);
		}
		long live = classes.countByInstructorIdAndStatusNot(id, OfflineClass.STATUS_CANCELLED);
		if (live > 0) {
			throw state(live + (live == 1 ? " offline class still uses" : " offline classes still use")
					+ " this instructor (not cancelled), so the instructor cannot be archived.");
		}
		Instant now = clock.instant();
		instructor.setArchivedAt(now);
		instructor.setUpdatedAt(now);
		instructors.saveAndFlush(instructor);
		audit.record(ARCHIVED, ENTITY, id);
		return view(instructor);
	}

	InstructorView view(OfflineInstructor instructor) {
		String[] aliases = instructor.getAliases() == null ? new String[0] : instructor.getAliases();
		return new InstructorView(instructor.getId(), instructor.getName(), instructor.getPhone(), instructor.getNote(),
				List.of(aliases), instructor.getArchivedAt() != null, instructor.getVersion() == null ? 0L : instructor.getVersion());
	}

	private OfflineInstructor find(UUID id) {
		return instructors.findById(id).orElseThrow(OfflineSupport::notFound);
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

	private static ApiException stale() {
		return new ApiException(ErrorCode.STALE_VERSION, HttpStatus.CONFLICT,
				"Someone else changed this instructor first. Reload the page and try again.");
	}

	private static FieldErrorDetail duplicate() {
		return new FieldErrorDetail("name", "An instructor with this name already exists.");
	}

	private static String name(JsonNode body, List<FieldErrorDetail> problems) {
		if (!OfflineSupport.has(body, "name")) {
			problems.add(new FieldErrorDetail("name", "This field is required."));
			return null;
		}
		String value = body.get("name").asText().trim();
		if (value.isEmpty()) {
			problems.add(new FieldErrorDetail("name", "This field is required."));
			return null;
		}
		if (value.length() > MAX_NAME) {
			problems.add(new FieldErrorDetail("name", "This is longer than " + MAX_NAME + " characters."));
			return null;
		}
		return value;
	}

	/** A text field: null or blank becomes null, longer than the column allows is a problem for that field. */
	private static String optionalText(JsonNode body, String key, int max, List<FieldErrorDetail> problems) {
		if (body == null || !body.has(key) || body.get(key).isNull()) {
			return null;
		}
		String value = body.get(key).asText().trim();
		if (value.isEmpty()) {
			return null;
		}
		if (value.length() > max) {
			problems.add(new FieldErrorDetail(key, "This is longer than " + max + " characters."));
			return null;
		}
		return value;
	}

	/** The alternative names: trimmed, blanks and repeats (ignoring case) dropped, at most {@value #MAX_ALIASES} of them. */
	private static String[] aliases(JsonNode value, List<FieldErrorDetail> problems) {
		if (value == null || value.isNull()) {
			return new String[0];
		}
		if (!value.isArray()) {
			problems.add(new FieldErrorDetail("aliases", "Enter the names as a list."));
			return new String[0];
		}
		Set<String> seen = new LinkedHashSet<>();
		Set<String> lower = new LinkedHashSet<>();
		for (JsonNode item : value) {
			String text = item.asText().trim();
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

	/** One instructor as the staff and the Master Admin see them. {@code version} is what a later edit must send back. */
	public record InstructorView(UUID id, String name, String phone, String note, List<String> aliases, boolean archived,
			long version) {
	}

}
