package com.coachingcenter.api.exams;

import java.util.List;

/** The field-by-field change list that the Master Admin reads before publishing a staged edit (NR-01, E31). */
record ChangeList(String revisionId, List<FieldChange> fields, List<QuestionChange> questions, PaperChange paper,
		List<MembershipChange> packageExams, Impact impact) {
}
