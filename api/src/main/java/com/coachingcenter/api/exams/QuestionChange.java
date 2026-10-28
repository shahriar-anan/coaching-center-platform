package com.coachingcenter.api.exams;

import java.util.UUID;

/** One question's change in a staged edit. {@code change} is ADDED, REMOVED, STEM, OPTIONS_TEXT, CORRECT, MARKS, EXPLANATION or MULTIPLE. */
record QuestionChange(UUID questionId, Integer position, String change, Object before, Object after) {
}
