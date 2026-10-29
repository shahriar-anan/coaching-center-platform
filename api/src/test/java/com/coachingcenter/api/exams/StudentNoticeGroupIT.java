package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.PostgresIntegrationTest;

/** The notice tabs (N1) against real PostgreSQL: one tab's rows, paging, counts, and read-all per tab. Rolls back. */
@SpringBootTest
@Transactional
class StudentNoticeGroupIT extends PostgresIntegrationTest {

	private static final Instant AT = Instant.parse("2026-10-29T04:00:00Z");

	@Autowired
	private StudentInboxService inbox;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void aTabListsOnlyItsOwnNoticesAndPagesWithin() {
		UUID student = insertUser();
		UUID other = insertUser();
		notice(student, "MCQ_SCORE", false);
		notice(student, "WRITTEN_RESULT", false);
		notice(student, "EXAM_OPEN", false);
		notice(student, "PURCHASE_GRANTED", false);
		notice(student, "ANNOUNCEMENT", false);
		notice(other, "MCQ_SCORE", false);

		assertThat(inbox.list(student, NoticeGroup.RESULTS, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(2);
		assertThat(inbox.list(student, NoticeGroup.EXAMS, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(2);
		assertThat(inbox.list(student, NoticeGroup.ANNOUNCEMENTS, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(1);
		assertThat(inbox.list(student, null, PageRequest.of(0, 20)).getTotalElements()).as("no tab means every notice")
			.isEqualTo(5);

		var secondPage = inbox.list(student, NoticeGroup.RESULTS, PageRequest.of(1, 1));
		assertThat(secondPage.getContent()).hasSize(1);
		assertThat(secondPage.getTotalElements()).isEqualTo(2);
		assertThat(secondPage.getContent().get(0).group()).isEqualTo("RESULTS");
	}

	@Test
	void unreadCountsAreKeptPerTab() {
		UUID student = insertUser();
		notice(student, "MCQ_SCORE", false);
		notice(student, "WRITTEN_RESULT", true);
		notice(student, "EXAM_OPEN", false);
		notice(student, "ANNOUNCEMENT", false);

		StudentInboxService.UnreadCounts counts = inbox.unreadCount(student);

		assertThat(counts.count()).isEqualTo(3);
		assertThat(counts.byGroup()).containsEntry("RESULTS", 1).containsEntry("EXAMS", 1)
			.containsEntry("ANNOUNCEMENTS", 1);
	}

	@Test
	void readAllMarksOnlyTheTabNamed() {
		UUID student = insertUser();
		notice(student, "MCQ_SCORE", false);
		notice(student, "WRITTEN_RESULT", false);
		notice(student, "EXAM_OPEN", false);
		notice(student, "ANNOUNCEMENT", false);

		assertThat(inbox.markAllRead(student, NoticeGroup.RESULTS)).isEqualTo(2);

		StudentInboxService.UnreadCounts counts = inbox.unreadCount(student);
		assertThat(counts.byGroup()).containsEntry("RESULTS", 0).containsEntry("EXAMS", 1)
			.containsEntry("ANNOUNCEMENTS", 1);
		assertThat(inbox.markAllRead(student, null)).as("the rest, with no tab").isEqualTo(2);
		assertThat(inbox.unreadCount(student).count()).isZero();
	}

	@Test
	void anotherStudentsNoticesNeverAppearInATab() {
		UUID student = insertUser();
		UUID other = insertUser();
		notice(other, "MCQ_SCORE", false);
		notice(other, "ANNOUNCEMENT", false);

		assertThat(inbox.list(student, NoticeGroup.RESULTS, PageRequest.of(0, 20)).getTotalElements()).isZero();
		assertThat(inbox.unreadCount(student).count()).isZero();
		assertThat(inbox.markAllRead(student, NoticeGroup.RESULTS)).isZero();
		assertThat(inbox.unreadCount(other).count()).as("the other student's notices are untouched").isEqualTo(2);
	}

	private void notice(UUID student, String kind, boolean read) {
		jdbc.update("insert into student_notices (id, user_id, kind, message_key, exam_id, participant_id, created_at, read_at)"
				+ " values (?, ?, ?, ?, null, null, ?, ?)", UUID.randomUUID(), student, kind, UUID.randomUUID().toString(),
				Timestamp.from(AT), read ? Timestamp.from(AT) : null);
	}

	private UUID insertUser() {
		UUID id = UUID.randomUUID();
		long digits = java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L);
		jdbc.update("insert into users (id, phone, email, role, status, created_at, updated_at)"
				+ " values (?, ?, ?, 'STUDENT', 'ACTIVE', now(), now())", id, "+8801" + String.format("%09d", digits),
				id + "@example.com");
		return id;
	}

}
