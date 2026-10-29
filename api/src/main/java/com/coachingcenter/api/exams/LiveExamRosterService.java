package com.coachingcenter.api.exams;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.PhoneNumbers;
import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.users.StudentProfileRepository;
import com.coachingcenter.api.users.User;
import com.coachingcenter.api.users.UserRepository;

import tools.jackson.databind.JsonNode;

@Service
public class LiveExamRosterService {

	static final int MAX_SIZE = 100;

	private static final String ROSTER_FROM = """
			FROM live_exam_roster r
			JOIN users u ON u.id = r.student_id
			LEFT JOIN student_profiles sp ON sp.user_id = u.id
			""";

	private static final String ROSTER_WHERE = """
			WHERE r.module_id = :moduleId AND u.deleted_at IS NULL
			""";

	private static final String ROSTER_QUERY_FILTER = """
			AND (:q = '' OR sp.full_name ILIKE concat('%', :q, '%') OR u.phone ILIKE concat('%', :q, '%')
			  OR u.email ILIKE concat('%', :q, '%') OR sp.student_code ILIKE concat('%', :q, '%'))
			""";

	private final LiveExamRosterRepository roster;

	private final NamedParameterJdbcTemplate jdbc;

	private final LiveExamModuleRepository modules;

	private final UserRepository users;

	private final StudentProfileRepository profiles;

	private final ExamMailService examMail;

	private final AfterCommit afterCommit;

	private final Clock clock;

	public LiveExamRosterService(LiveExamRosterRepository roster, LiveExamModuleRepository modules, UserRepository users,
			StudentProfileRepository profiles, ExamMailService examMail, AfterCommit afterCommit, Clock clock,
			JdbcTemplate jdbc) {
		this.roster = roster;
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
		this.modules = modules;
		this.users = users;
		this.profiles = profiles;
		this.examMail = examMail;
		this.afterCommit = afterCommit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<StudentMatch> search(UUID moduleId, String query) {
		requireModule(moduleId);
		String q = query == null ? "" : query.trim();
		if (q.isEmpty()) {
			return List.of();
		}
		if (q.contains("@")) {
			return users.findStudentByEmail(q).map(this::match).stream().toList();
		}
		if (looksLikePhone(q)) {
			try {
				return users.findByPhoneAndDeletedAtIsNull(PhoneNumbers.normalize(q))
					.filter(user -> Role.STUDENT.name().equals(user.getRole()))
					.map(this::match)
					.stream()
					.toList();
			}
			catch (ApiException exception) {
				return List.of();
			}
		}
		return users.findStudentsByNameOrCode(q).stream().map(this::match).toList();
	}

	@Transactional
	public StudentMatch mark(UUID moduleId, UUID studentId) {
		requirePublished(moduleId);
		User student = requireStudent(studentId);
		if (!roster.existsByModuleIdAndStudentId(moduleId, studentId)) {
			LiveExamRoster row = new LiveExamRoster();
			row.setModuleId(moduleId);
			row.setStudentId(studentId);
			row.setCreatedAt(clock.instant());
			roster.save(row);
			afterCommit.run(() -> examMail.onRosterAdded(moduleId, studentId));
		}
		return match(student);
	}

	@Transactional
	public PasteResult paste(UUID moduleId, JsonNode body) {
		requireModule(moduleId);
		JsonNode values = body.get("values");
		if (values == null || !values.isArray()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
		List<StudentMatch> marked = new ArrayList<>();
		List<String> unknown = new ArrayList<>();
		for (JsonNode value : values) {
			if (value == null || value.isNull() || value.asText().isBlank()) {
				continue;
			}
			String raw = value.asText().trim();
			User student = resolveExact(raw);
			if (student == null) {
				unknown.add(raw);
			}
			else {
				marked.add(mark(moduleId, student.getId()));
			}
		}
		return new PasteResult(marked, unknown);
	}

	@Transactional
	public void remove(UUID moduleId, UUID studentId) {
		requireModule(moduleId);
		roster.deleteByModuleIdAndStudentId(moduleId, studentId);
	}

	/**
	 * The module's roster in name order. {@code q} matches name, phone, email or student code. {@code startedAny} is
	 * true when the student has a sitting on any exam of this module, so removing them keeps that result.
	 */
	@Transactional(readOnly = true)
	public Page<RosterRow> rows(UUID moduleId, String query, Pageable pageable) {
		requireModule(moduleId);
		String q = query == null ? "" : query.trim();
		int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_SIZE);
		Pageable page = PageRequest.of(pageable.getPageNumber(), size);
		MapSqlParameterSource params = new MapSqlParameterSource("moduleId", moduleId).addValue("q", q);
		Long total = jdbc.queryForObject("SELECT count(*) " + ROSTER_FROM + ROSTER_WHERE + ROSTER_QUERY_FILTER, params,
				Long.class);
		params.addValue("limit", size).addValue("offset", (long) page.getPageNumber() * size);
		List<RosterRow> rows = jdbc.query("""
				SELECT r.student_id, u.phone, u.email, sp.full_name, sp.student_code, r.created_at,
				  exists (
				    select 1 from live_exam_participants p
				    join live_exams e on e.id = p.exam_id
				    where e.module_id = r.module_id and p.student_id = r.student_id and p.deleted_at is null
				  ) as started_any
				""" + ROSTER_FROM + ROSTER_WHERE + ROSTER_QUERY_FILTER + """
				ORDER BY sp.full_name NULLS LAST, sp.student_code, r.student_id
				LIMIT :limit OFFSET :offset
				""", params,
				(rs, number) -> new RosterRow(rs.getObject("student_id", UUID.class), rs.getString("full_name"),
						rs.getString("phone"), rs.getString("email"), rs.getString("student_code"),
						rs.getTimestamp("created_at").toInstant(), rs.getBoolean("started_any")));
		return new PageImpl<>(rows, page, total == null ? 0 : total);
	}

	public boolean marked(UUID moduleId, UUID studentId) {
		return roster.existsByModuleIdAndStudentId(moduleId, studentId);
	}

	private User resolveExact(String raw) {
		if (raw.contains("@")) {
			return users.findStudentByEmail(raw).orElse(null);
		}
		try {
			return users.findByPhoneAndDeletedAtIsNull(PhoneNumbers.normalize(raw))
				.filter(user -> Role.STUDENT.name().equals(user.getRole()))
				.orElse(null);
		}
		catch (ApiException exception) {
			return null;
		}
	}

	private static boolean looksLikePhone(String value) {
		String trimmed = value.replace(" ", "");
		return trimmed.startsWith("+") || trimmed.startsWith("01");
	}

	private User requireStudent(UUID studentId) {
		User user = users.findByIdAndDeletedAtIsNull(studentId).orElseThrow(this::notFound);
		if (!Role.STUDENT.name().equals(user.getRole())) {
			throw notFound();
		}
		return user;
	}

	private void requireModule(UUID moduleId) {
		modules.findByIdAndDeletedAtIsNull(moduleId).orElseThrow(this::notFound);
	}

	/** A student can be added directly only to a published package (archived, draft or withdrawn ones refuse new enrollments; existing rows stay). */
	private void requirePublished(UUID moduleId) {
		LiveExamModule module = modules.findByIdAndDeletedAtIsNull(moduleId).orElseThrow(this::notFound);
		if (!LiveExamModule.PUBLISHED.equals(module.getStatus())) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Direct enrollment needs a published package.");
		}
	}

	private StudentMatch match(User user) {
		String name = profiles.findById(user.getId()).map(profile -> profile.getFullName()).orElse("");
		return new StudentMatch(user.getId(), name, user.getPhone(), user.getEmail());
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	public record StudentMatch(UUID id, String fullName, String phone, String email) {
	}

	public record PasteResult(List<StudentMatch> marked, List<String> unknown) {
	}

	public record RosterRow(UUID studentId, String fullName, String phone, String email, String studentCode,
			Instant addedAt, boolean startedAny) {
	}

}
