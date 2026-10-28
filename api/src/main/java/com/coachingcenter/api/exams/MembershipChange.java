package com.coachingcenter.api.exams;

import java.util.UUID;

/** An exam added to or removed from a package in a staged edit. {@code change} is ADDED or REMOVED. */
record MembershipChange(UUID examId, String titleEn, String titleBn, String change) {
}
