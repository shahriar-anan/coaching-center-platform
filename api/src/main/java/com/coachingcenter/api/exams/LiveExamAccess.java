package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class LiveExamAccess {

	private final LiveExamRosterRepository roster;

	private final LiveExamGrantRepository grants;

	public LiveExamAccess(LiveExamRosterRepository roster, LiveExamGrantRepository grants) {
		this.roster = roster;
		this.grants = grants;
	}

	public boolean hasExam(UUID studentId, LiveExam exam) {
		return roster.existsByModuleIdAndStudentId(exam.getModuleId(), studentId)
				|| grants.existsByStudentIdAndScopeAndModuleId(studentId, LiveExamOrder.MODULE, exam.getModuleId())
				|| grants.existsByStudentIdAndScopeAndExamId(studentId, LiveExamOrder.EXAM, exam.getId());
	}

	public boolean hasModule(UUID studentId, UUID moduleId) {
		return roster.existsByModuleIdAndStudentId(moduleId, studentId)
				|| grants.existsByStudentIdAndModuleId(studentId, moduleId);
	}

}
