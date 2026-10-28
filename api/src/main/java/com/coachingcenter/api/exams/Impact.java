package com.coachingcenter.api.exams;

/** What publishing a staged edit will do beyond the change itself: re-score papers, and notify students. */
record Impact(int papersToRescore, boolean notifiesStudents, int studentsToNotify) {
}
