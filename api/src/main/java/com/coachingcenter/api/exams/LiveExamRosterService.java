package com.coachingcenter.api.exams;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
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

	private final LiveExamRosterRepository roster;

	private final LiveExamModuleRepository modules;

	private final UserRepository users;

	private final StudentProfileRepository profiles;

	private final Clock clock;

	public LiveExamRosterService(LiveExamRosterRepository roster, LiveExamModuleRepository modules, UserRepository users,
			StudentProfileRepository profiles, Clock clock) {
		this.roster = roster;
		this.modules = modules;
		this.users = users;
		this.profiles = profiles;
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
		return users.findStudentsByName(q).stream().map(this::match).toList();
	}

	@Transactional
	public StudentMatch mark(UUID moduleId, UUID studentId) {
		requireModule(moduleId);
		User student = requireStudent(studentId);
		if (!roster.existsByModuleIdAndStudentId(moduleId, studentId)) {
			LiveExamRoster row = new LiveExamRoster();
			row.setModuleId(moduleId);
			row.setStudentId(studentId);
			row.setCreatedAt(clock.instant());
			roster.save(row);
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

}
