package com.coachingcenter.api.exams;

import java.time.Instant;

/**
 * A student's access to one exam. {@code source} is ROSTER, MODULE or EXAM for now. {@code endsAt} is null when access never
 * ends, and {@code grantStart} is when the access began (both null when not granted).
 */
record AccessState(boolean granted, String source, Instant grantStart, Instant endsAt, boolean expired) {

	static final AccessState NONE = new AccessState(false, null, null, null, false);

}
