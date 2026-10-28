package com.coachingcenter.api.exams;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * A student's favourites: exams and packages, marked without buying (NR-01, R7 and B6). Only a published item in a visible
 * branch can be marked; one that is hidden later is skipped on the list, not deleted.
 */
@Service
public class FavouriteService {

	private final StudentFavouriteRepository favourites;

	private final LiveExamRepository exams;

	private final LiveExamModuleRepository modules;

	private final LiveExamCatalogVisibility visibility;

	private final LiveExamPublicService catalog;

	private final Clock clock;

	public FavouriteService(StudentFavouriteRepository favourites, LiveExamRepository exams,
			LiveExamModuleRepository modules, LiveExamCatalogVisibility visibility, LiveExamPublicService catalog,
			Clock clock) {
		this.favourites = favourites;
		this.exams = exams;
		this.modules = modules;
		this.visibility = visibility;
		this.catalog = catalog;
		this.clock = clock;
	}

	/** Idempotent: marking a marked exam changes nothing. */
	@Transactional
	public void putExam(UUID studentId, UUID examId) {
		LiveExam exam = exams.findByIdAndDeletedAtIsNull(examId).orElseThrow(this::notFound);
		if (!LiveExam.PUBLISHED.equals(exam.getStatus()) || visibility.branchHidden(exam.getCategoryId())) {
			throw notFound();
		}
		if (favourites.findByStudentIdAndExamId(studentId, examId).isPresent()) {
			return;
		}
		StudentFavourite row = new StudentFavourite();
		row.setStudentId(studentId);
		row.setExamId(examId);
		row.setCreatedAt(clock.instant());
		favourites.save(row);
	}

	@Transactional
	public void removeExam(UUID studentId, UUID examId) {
		favourites.findByStudentIdAndExamId(studentId, examId).ifPresent(favourites::delete);
	}

	@Transactional
	public void putModule(UUID studentId, UUID moduleId) {
		LiveExamModule module = modules.findByIdAndDeletedAtIsNull(moduleId).orElseThrow(this::notFound);
		if (!LiveExamModule.PUBLISHED.equals(module.getStatus()) || visibility.branchHidden(module.getCategoryId())) {
			throw notFound();
		}
		if (favourites.findByStudentIdAndModuleId(studentId, moduleId).isPresent()) {
			return;
		}
		StudentFavourite row = new StudentFavourite();
		row.setStudentId(studentId);
		row.setModuleId(moduleId);
		row.setCreatedAt(clock.instant());
		favourites.save(row);
	}

	@Transactional
	public void removeModule(UUID studentId, UUID moduleId) {
		favourites.findByStudentIdAndModuleId(studentId, moduleId).ifPresent(favourites::delete);
	}

	/** The student's marked exams, in the public row shape with pricing. Hidden ones are skipped. */
	@Transactional(readOnly = true)
	public Favourites list(UUID studentId, StudentOwnAccessService.Viewer viewer) {
		List<StudentFavourite> marks = favourites.findByStudentId(studentId).stream()
			.sorted(Comparator.comparing(StudentFavourite::getCreatedAt).thenComparing(StudentFavourite::getId))
			.toList();
		Map<UUID, LiveExam> examById = new HashMap<>();
		Map<UUID, LiveExamModule> moduleById = new HashMap<>();
		List<UUID> examIds = marks.stream().map(StudentFavourite::getExamId).filter(id -> id != null).toList();
		List<UUID> moduleIds = marks.stream().map(StudentFavourite::getModuleId).filter(id -> id != null).toList();
		for (LiveExam exam : exams.findAllById(examIds)) {
			examById.put(exam.getId(), exam);
		}
		for (LiveExamModule module : modules.findAllById(moduleIds)) {
			moduleById.put(module.getId(), module);
		}
		List<LiveExamPublicService.PublicExam> examRows = new ArrayList<>();
		List<LiveExamPublicService.PublicModule> moduleRows = new ArrayList<>();
		for (StudentFavourite mark : marks) {
			if (mark.getExamId() != null) {
				LiveExam exam = examById.get(mark.getExamId());
				if (exam != null && exam.getDeletedAt() == null && LiveExam.PUBLISHED.equals(exam.getStatus())
						&& !visibility.branchHidden(exam.getCategoryId())) {
					examRows.add(catalog.examRowFor(exam, viewer).withFavourite(true));
				}
			}
			else {
				LiveExamModule module = moduleById.get(mark.getModuleId());
				if (module != null && module.getDeletedAt() == null && LiveExamModule.PUBLISHED.equals(module.getStatus())
						&& !visibility.branchHidden(module.getCategoryId())) {
					moduleRows.add(catalog.moduleRow(module, viewer).withFavourite(true));
				}
			}
		}
		return new Favourites(examRows, moduleRows);
	}

	/** The exam ids a student has marked, for the flag on a public row. */
	@Transactional(readOnly = true)
	public Set<UUID> examIds(UUID studentId) {
		Set<UUID> ids = new HashSet<>();
		for (StudentFavourite mark : favourites.findByStudentId(studentId)) {
			if (mark.getExamId() != null) {
				ids.add(mark.getExamId());
			}
		}
		return ids;
	}

	@Transactional(readOnly = true)
	public Set<UUID> moduleIds(UUID studentId) {
		Set<UUID> ids = new HashSet<>();
		for (StudentFavourite mark : favourites.findByStudentId(studentId)) {
			if (mark.getModuleId() != null) {
				ids.add(mark.getModuleId());
			}
		}
		return ids;
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	/** {@code GET /me/favourites}: exams and packages, each in the public row shape. */
	public record Favourites(List<LiveExamPublicService.PublicExam> exams,
			List<LiveExamPublicService.PublicModule> packages) {
	}

}
