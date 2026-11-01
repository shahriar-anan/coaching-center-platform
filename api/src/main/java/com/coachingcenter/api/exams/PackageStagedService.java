package com.coachingcenter.api.exams;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * The staged edits of one package and of its exams, read and published together (NR-01, B4 and E31). The package screen reads
 * the summary, and the Master Admin publishes everything in one call: the package's staged revision and every member exam's
 * staged revision are published in one transaction, so when any step fails nothing is published.
 */
@Service
public class PackageStagedService {

	/** The name a teacher change carries in an exam's change list (see {@link ExamChangeListBuilder}). */
	private static final String INSTRUCTOR_FIELD = "instructor";

	private final LiveExamModuleRepository modules;

	private final LiveExamPackageExamRepository memberships;

	private final LiveExamRepository exams;

	private final PackageRevisionService packages;

	private final LiveExamRevisionService examRevisions;

	private final LiveExamService liveExams;

	public PackageStagedService(LiveExamModuleRepository modules, LiveExamPackageExamRepository memberships,
			LiveExamRepository exams, PackageRevisionService packages, LiveExamRevisionService examRevisions,
			LiveExamService liveExams) {
		this.modules = modules;
		this.memberships = memberships;
		this.exams = exams;
		this.packages = packages;
		this.examRevisions = examRevisions;
		this.liveExams = liveExams;
	}

	/**
	 * Whether the package has a staged revision, and every member exam that has one with its number of changes. A teacher change
	 * on any of those exams sets {@code instructorChanges}.
	 */
	@Transactional(readOnly = true)
	public StagedSummary summary(UUID moduleId) {
		requireModule(moduleId);
		List<ExamSummary> rows = new ArrayList<>();
		boolean instructorChanges = false;
		for (UUID examId : activeExamIds(moduleId)) {
			if (!examRevisions.hasRevision(examId)) {
				continue;
			}
			LiveExam exam = exams.findByIdAndDeletedAtIsNull(examId).orElse(null);
			if (exam == null) {
				continue;
			}
			ChangeList changes = examRevisions.changeList(examId);
			for (FieldChange field : changes.fields()) {
				instructorChanges |= INSTRUCTOR_FIELD.equals(field.field());
			}
			rows.add(new ExamSummary(examId, exam.getTitleEn(), exam.getTitleBn(), count(changes)));
		}
		return new StagedSummary(packages.hasRevision(moduleId), rows, instructorChanges);
	}

	/**
	 * Publishes the package's staged revision, then each member exam's, in one transaction. {@code packageRevisionId} and the
	 * values of {@code examRevisionIds} are the revision ids the Master Admin read; a stale one is refused and nothing is published.
	 * An exam id in {@code examRevisionIds} that has nothing staged is refused the same way.
	 */
	@Transactional
	public PublishedChanges publishChanges(UUID moduleId, String packageRevisionId, Map<UUID, String> examRevisionIds,
			UUID actorId) {
		LiveExamModule module = requireModule(moduleId);
		if (LiveExamModule.ARCHIVED.equals(module.getStatus())) {
			throw new ApiException(ErrorCode.MODULE_ARCHIVED, HttpStatus.CONFLICT, "An archived module cannot be changed.");
		}
		boolean packageChanged = packages.hasRevision(moduleId);
		if (packageChanged) {
			packages.publish(module, packageRevisionId);
		}
		else if (packageRevisionId != null) {
			throw stale();
		}
		List<UUID> staged = new ArrayList<>();
		for (UUID examId : activeExamIds(moduleId)) {
			if (examRevisions.hasRevision(examId)) {
				staged.add(examId);
			}
		}
		for (UUID examId : examRevisionIds.keySet()) {
			if (!staged.contains(examId)) {
				throw stale();
			}
		}
		for (UUID examId : staged) {
			liveExams.publish(examId, examRevisionIds.get(examId), actorId);
		}
		return new PublishedChanges(packageChanged, staged.size());
	}

	private static int count(ChangeList changes) {
		return changes.fields().size() + changes.questions().size() + (changes.paper() == null ? 0 : 1);
	}

	private List<UUID> activeExamIds(UUID moduleId) {
		return memberships.findActiveByModuleId(moduleId).stream().map(LiveExamPackageExam::getExamId).toList();
	}

	private LiveExamModule requireModule(UUID moduleId) {
		return modules.findByIdAndDeletedAtIsNull(moduleId)
			.orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."));
	}

	private static ApiException stale() {
		return new ApiException(ErrorCode.PENDING_CHANGES_STALE, HttpStatus.CONFLICT,
				"The staged changes have changed since they were read.");
	}

	/** What is waiting on a package: its own changes, each member exam with changes and its count, and any teacher change. */
	public record StagedSummary(boolean packageChanges, List<ExamSummary> exams, boolean instructorChanges) {
	}

	public record ExamSummary(UUID id, String titleEn, String titleBn, int changes) {
	}

	/** What the combined publish applied: whether the package's revision was published, and how many exams' were. */
	public record PublishedChanges(boolean packageChanged, int examsPublished) {
	}

}
