package com.coachingcenter.api.exams;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

/**
 * The exams of a bundle order (plan D1): one order, one module, 1 to {@value #MAX_EXAMS} exams in
 * {@code live_exam_order_exams}. The table has no unique index for an open bundle, so callers serialise a student's bundle
 * inserts with {@link #lockStudent} and match an open order by its exact exam set.
 */
@Component
class LiveExamOrderBundle {

	static final String SCOPE = "BUNDLE";

	static final int MAX_EXAMS = 20;

	private final JdbcTemplate jdbc;

	LiveExamOrderBundle(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Holds until the transaction ends, so two bundle requests from one student cannot both insert. */
	void lockStudent(UUID studentId) {
		jdbc.query("select pg_advisory_xact_lock(hashtext(?))", (RowCallbackHandler) rs -> {
		}, "live-exam-bundle:" + studentId);
	}

	void insertExams(UUID orderId, Collection<UUID> examIds) {
		for (UUID examId : examIds) {
			jdbc.update("insert into live_exam_order_exams (order_id, exam_id) values (?, ?)", orderId, examId);
		}
	}

	/** The bundle's exams, in exam time order (undated last). */
	List<UUID> examIds(UUID orderId) {
		return jdbc.queryForList("""
				select x.exam_id from live_exam_order_exams x
				join live_exams e on e.id = x.exam_id
				where x.order_id = ?
				order by e.live_at nulls last, e.id
				""", UUID.class, orderId);
	}

	/** The student's open bundle order in this module whose exam set is exactly {@code examIds}. */
	Optional<UUID> findOpen(UUID studentId, UUID moduleId, Collection<UUID> examIds) {
		Set<UUID> wanted = new HashSet<>(examIds);
		List<UUID> open = jdbc.queryForList("""
				select id from live_exam_orders
				where student_id = ? and module_id = ? and scope = ? and status = ?
				order by created_at, id
				""", UUID.class, studentId, moduleId, SCOPE, LiveExamOrder.INITIATED);
		for (UUID orderId : open) {
			if (new HashSet<>(examIds(orderId)).equals(wanted)) {
				return Optional.of(orderId);
			}
		}
		return Optional.empty();
	}

	record Inserted(LiveExamOrder order, boolean created) {
	}

}
