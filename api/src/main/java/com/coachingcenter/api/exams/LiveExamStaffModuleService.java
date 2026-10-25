package com.coachingcenter.api.exams;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.users.AdminProfileRepository;
import com.coachingcenter.api.users.User;
import com.coachingcenter.api.users.UserRepository;

/** Staff reads of live exam modules. These carry the category path, the teacher and the exam counts. */
@Service
public class LiveExamStaffModuleService {

	private static final UUID ANY_CATEGORY = new UUID(0L, 0L);

	private static final Set<String> STATUSES = Set.of(LiveExamModule.DRAFT, LiveExamModule.PUBLISHED, LiveExamModule.ARCHIVED);

	private static final int MAX_CATEGORY_DEPTH = 32;

	private final LiveExamModuleService moduleViews;

	private final LiveExamModuleRepository modules;

	private final LiveExamCategoryRepository categories;

	private final LiveExamRepository exams;

	private final LiveExamInstructorRepository instructors;

	private final UserRepository users;

	private final AdminProfileRepository adminProfiles;

	public LiveExamStaffModuleService(LiveExamModuleService moduleViews, LiveExamModuleRepository modules,
			LiveExamCategoryRepository categories, LiveExamRepository exams, LiveExamInstructorRepository instructors,
			UserRepository users, AdminProfileRepository adminProfiles) {
		this.moduleViews = moduleViews;
		this.modules = modules;
		this.categories = categories;
		this.exams = exams;
		this.instructors = instructors;
		this.users = users;
		this.adminProfiles = adminProfiles;
	}

	/** Master Admin and System Admin. {@code status} is optional; {@code q} matches either title, case-insensitively. */
	@Transactional(readOnly = true)
	public Page<LiveExamModuleService.ModuleView> list(UUID categoryId, String status, String q, Pageable pageable) {
		String state = status == null ? "" : status.trim().toUpperCase();
		if (!state.isEmpty() && !STATUSES.contains(state)) {
			throw validationFailed();
		}
		String search = q == null ? "" : q.trim().toLowerCase();
		Page<LiveExamModule> page = modules.searchForStaff(categoryId == null,
				categoryId == null ? ANY_CATEGORY : categoryId, state, "%" + search + "%",
				LiveExamModuleService.ordered(pageable));
		Context context = context(page.getContent());
		return page.map(module -> view(module, context));
	}

	/** An instructor sees only the modules assigned to them, each with the category path they need. */
	@Transactional(readOnly = true)
	public Page<LiveExamModuleService.ModuleView> listForInstructor(UUID instructorId, Pageable pageable) {
		Page<LiveExamModule> page = modules.findAssignedToInstructor(instructorId, LiveExamModuleService.ordered(pageable));
		Context context = context(page.getContent());
		return page.map(module -> view(module, context));
	}

	@Transactional(readOnly = true)
	public LiveExamModuleService.ModuleView get(UUID id) {
		LiveExamModule module = require(id);
		return view(module, context(List.of(module)));
	}

	/** An instructor reading a module they are not assigned to gets 404, the same as a missing module. */
	@Transactional(readOnly = true)
	public LiveExamModuleService.ModuleView getAssigned(UUID id, UUID instructorId) {
		LiveExamModule module = require(id);
		LiveExamInstructor assignment = instructors.findById(id).orElseThrow(this::notFound);
		if (!instructorId.equals(assignment.getInstructorId())) {
			throw notFound();
		}
		return view(module, context(List.of(module)));
	}

	private LiveExamModuleService.ModuleView view(LiveExamModule module, Context context) {
		Counts counts = context.counts().getOrDefault(module.getId(), Counts.NONE);
		return moduleViews.staffView(module, pathOf(module.getCategoryId(), context.categories()),
				teacherOf(module.getId(), context), counts.mcq(), counts.written());
	}

	/** Every live category that is not deleted, by id. Loaded once per request; the table is small. */
	static Map<UUID, LiveExamCategory> categoriesById(LiveExamCategoryRepository categories) {
		Map<UUID, LiveExamCategory> byId = new HashMap<>();
		for (LiveExamCategory category : categories.findByDeletedAtIsNull()) {
			byId.put(category.getId(), category);
		}
		return byId;
	}

	/** The chain from the root category down to {@code categoryId}, root first. */
	static List<LiveExamModuleService.CategoryRef> pathOf(UUID categoryId, Map<UUID, LiveExamCategory> categories) {
		Deque<LiveExamModuleService.CategoryRef> path = new ArrayDeque<>();
		UUID current = categoryId;
		for (int depth = 0; current != null && depth < MAX_CATEGORY_DEPTH; depth++) {
			LiveExamCategory category = categories.get(current);
			if (category == null) {
				break;
			}
			path.addFirst(new LiveExamModuleService.CategoryRef(category.getId(), category.getTitleEn(), category.getTitleBn()));
			current = category.getParentId();
		}
		return List.copyOf(path);
	}

	/** One round of queries for a whole page: categories, counts, teachers. */
	private Context context(List<LiveExamModule> rows) {
		Map<UUID, LiveExamCategory> byId = categoriesById(categories);
		List<UUID> moduleIds = rows.stream().map(LiveExamModule::getId).toList();
		Map<UUID, UUID> teacherIds = new HashMap<>();
		Map<UUID, User> teachers = new HashMap<>();
		Map<UUID, String> names = new HashMap<>();
		if (!moduleIds.isEmpty()) {
			for (LiveExamInstructor assignment : instructors.findAllById(moduleIds)) {
				teacherIds.put(assignment.getModuleId(), assignment.getInstructorId());
			}
		}
		if (!teacherIds.isEmpty()) {
			List<UUID> userIds = new ArrayList<>(teacherIds.values());
			for (User user : users.findAllById(userIds)) {
				if (user.getDeletedAt() == null) {
					teachers.put(user.getId(), user);
				}
			}
			adminProfiles.findAllById(userIds).forEach(profile -> names.put(profile.getUserId(), profile.getFullName()));
		}
		return new Context(byId, counts(moduleIds), teacherIds, teachers, names);
	}

	private Map<UUID, Counts> counts(List<UUID> moduleIds) {
		Map<UUID, Counts> byModule = new HashMap<>();
		if (moduleIds.isEmpty()) {
			return byModule;
		}
		for (Object[] row : exams.countByModuleAndType(moduleIds)) {
			UUID moduleId = (UUID) row[0];
			int total = ((Number) row[2]).intValue();
			Counts current = byModule.getOrDefault(moduleId, Counts.NONE);
			byModule.put(moduleId, LiveExam.MCQ.equals(row[1]) ? new Counts(total, current.written())
					: new Counts(current.mcq(), total));
		}
		return byModule;
	}

	private static LiveExamModuleService.TeacherRef teacherOf(UUID moduleId, Context context) {
		UUID teacherId = context.teacherIds().get(moduleId);
		if (teacherId == null) {
			return null;
		}
		User user = context.teachers().get(teacherId);
		return new LiveExamModuleService.TeacherRef(teacherId, context.names().get(teacherId),
				user == null ? null : user.getPhone());
	}

	private LiveExamModule require(UUID id) {
		return modules.findByIdAndDeletedAtIsNull(id).orElseThrow(this::notFound);
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	private record Counts(int mcq, int written) {

		static final Counts NONE = new Counts(0, 0);

	}

	private record Context(Map<UUID, LiveExamCategory> categories, Map<UUID, Counts> counts, Map<UUID, UUID> teacherIds,
			Map<UUID, User> teachers, Map<UUID, String> names) {
	}

}
