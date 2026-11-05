package com.coachingcenter.api.staffledger;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.staffledger.StaffPayCalculator.Line;
import com.coachingcenter.api.staffledger.StaffPayCalculator.Totals;
import com.coachingcenter.api.staffnotifications.StaffNotificationKind;
import com.coachingcenter.api.staffnotifications.StaffNotificationWriter;

/**
 * The earnings sync (staff pay plan, section 5, phase 4). It reads the marking columns that marking already writes and writes
 * only MARKING lines to the ledger. Marking and publishing code is never touched.
 *
 * <p>
 * {@link #syncSheets} inserts one MARKING line per newly published sheet that earns (see {@link MarkingPayRules}), with
 * {@code ON CONFLICT (participant_id) DO NOTHING}, so a sheet pays once even when it is corrected and published again. It then
 * tells the Master Admin and the instructor, in the same transaction as the lines: a failed run writes nothing, and the next
 * tick tries the same sheets again.
 *
 * <p>
 * {@link #remindUnpaid} tells the Master Admin about an instructor whose oldest unpaid marking is older than the reminder
 * days. The notification key holds the instructor and the ISO week, so it goes at most once a week.
 */
@Service
public class StaffEarningsService {

	/** Sheets handled per run. A backlog (the first run after release) clears over several ticks. */
	static final int BATCH_SIZE = 500;

	/** The Master Admin's page for one person, where the owed notice links. */
	static final String PERSON_LINK = "/staff/pay/";

	/** The instructor's own pay page, where the earned notice links. */
	static final String EARNED_LINK = "/staff/my-pay";

	/**
	 * The sheets that may earn, as the rule in {@link MarkingPayRules#earnsSheet} states it. Filtering here keeps sheets that
	 * can never earn (a Master Admin's mark) from using up the batch.
	 *
	 * <p>
	 * The rate is the exam's own rate, else the marker's rate, else the default (decision P18, as
	 * {@link ExamPayTermsService#effectiveRate} states it).
	 */
	private static final String CANDIDATES = """
			select p.id as participant_id, p.exam_id, p.evaluated_by, u.role as evaluator_role, p.published_at,
			  p.status, e.exam_type, p.deleted_at, e.title_en, e.title_bn, coalesce(a.full_name, '') as full_name,
			  coalesce(t.sheet_rate, s.sheet_rate, d.sheet_rate) as rate
			from live_exam_participants p
			join live_exams e on e.id = p.exam_id
			join users u on u.id = p.evaluated_by and u.role = 'INSTRUCTOR'
			join staff_pay_defaults d on d.id = 1
			left join staff_pay_settings s on s.user_id = u.id
			left join live_exam_pay_terms t on t.exam_id = e.id
			left join admin_profiles a on a.user_id = u.id
			where p.published_at is not null and p.deleted_at is null
			  and p.status = 'SUBMITTED' and e.exam_type = 'WRITTEN'
			  and not exists (select 1 from staff_ledger l where l.participant_id = p.id)
			order by p.published_at, p.id
			limit :limit
			""";

	private static final String INSERT_LINE = """
			insert into staff_ledger (id, user_id, kind, amount, due_on, participant_id, exam_id, sheet_rate, created_at)
			values (:id, :user, 'MARKING', :rate, :dueOn, :participant, :exam, :rate, :now)
			on conflict (participant_id) do nothing
			""";

	private static final String MASTER_ADMINS = """
			select id from users where role = 'MASTER_ADMIN' and status = 'ACTIVE' and deleted_at is null
			""";

	private static final String INSTRUCTORS_WITH_MARKING = """
			select u.id, coalesce(a.full_name, '') as full_name
			from users u left join admin_profiles a on a.user_id = u.id
			where u.role = 'INSTRUCTOR' and u.id in (select l.user_id from staff_ledger l where l.kind = 'MARKING')
			""";

	private static final String LEDGER_LINES = """
			select id, kind, amount, due_on, created_at, voided_at from staff_ledger where user_id = :user
			""";

	private final NamedParameterJdbcTemplate jdbc;

	private final StaffNotificationWriter notifications;

	private final Clock clock;

	public StaffEarningsService(JdbcTemplate jdbc, StaffNotificationWriter notifications, Clock clock) {
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
		this.notifications = notifications;
		this.clock = clock;
	}

	/** Writes the MARKING lines for newly published sheets and notifies. Returns how many lines were written. */
	@Transactional
	public int syncSheets() {
		List<Candidate> candidates = jdbc.query(CANDIDATES, new MapSqlParameterSource("limit", BATCH_SIZE),
				(rs, number) -> new Candidate(rs.getObject("participant_id", UUID.class), rs.getObject("exam_id", UUID.class),
						rs.getObject("evaluated_by", UUID.class), rs.getString("evaluator_role"),
						rs.getTimestamp("published_at").toInstant(), rs.getString("status"), rs.getString("exam_type"),
						rs.getTimestamp("deleted_at") != null, rs.getString("title_en"), rs.getString("title_bn"),
						rs.getString("full_name"), rs.getBigDecimal("rate")));
		Instant now = clock.instant();
		List<Earned> written = new ArrayList<>();
		for (Candidate candidate : candidates) {
			// The query already applies the rule; checking it again here keeps the rule's wording in one place.
			if (!MarkingPayRules.earnsSheet(candidate.evaluatorRole(), candidate.status(), candidate.examType(),
					candidate.publishedAt(), candidate.deleted())) {
				continue;
			}
			int inserted = jdbc.update(INSERT_LINE, new MapSqlParameterSource()
				.addValue("id", UUID.randomUUID())
				.addValue("user", candidate.userId())
				.addValue("rate", candidate.rate())
				.addValue("dueOn", java.sql.Date.valueOf(MarkingPayRules.publishDay(candidate.publishedAt())), Types.DATE)
				.addValue("participant", candidate.participantId())
				.addValue("exam", candidate.examId())
				.addValue("now", Timestamp.from(now)));
			if (inserted == 1) {
				written.add(new Earned(candidate));
			}
		}
		notifyPublished(groups(written), UUID.randomUUID(), today());
		return written.size();
	}

	/** Tells the Master Admin about each instructor whose oldest unpaid marking is past the reminder days. Returns how many were sent. */
	@Transactional
	public int remindUnpaid() {
		LocalDate today = today();
		int reminderDays = jdbc.queryForObject("select unpaid_reminder_days from staff_pay_defaults where id = 1",
				new MapSqlParameterSource(), Integer.class);
		List<Person> instructors = jdbc.query(INSTRUCTORS_WITH_MARKING, new MapSqlParameterSource(),
				(rs, number) -> new Person(rs.getObject("id", UUID.class), rs.getString("full_name")));
		List<UUID> masters = masterAdmins();
		String week = MarkingPayRules.isoWeek(today);
		int sent = 0;
		for (Person person : instructors) {
			List<Line> lines = lines(person.id());
			Totals totals = StaffPayCalculator.totals(lines, today);
			if (totals.owedNow().signum() <= 0) {
				continue;
			}
			Optional<LocalDate> oldest = MarkingPayRules.oldestUnpaidMarkingDue(lines);
			if (oldest.isEmpty() || !MarkingPayRules.reminderDue(oldest.get(), today, reminderDays)) {
				continue;
			}
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("personName", StaffNotificationWriter.fitText(person.fullName()));
			payload.put("amount", totals.owedNow());
			payload.put("days", ChronoUnit.DAYS.between(oldest.get(), today));
			for (UUID master : masters) {
				if (notifications.notify(master, StaffNotificationKind.MARKING_UNPAID_REMINDER, payload,
						PERSON_LINK + person.id(), "MARKING_UNPAID_REMINDER:" + master + ":" + person.id() + ":" + week)) {
					sent++;
				}
			}
		}
		return sent;
	}

	/** Groups the new lines by instructor and exam, so one exam's sheets make one notice each. */
	private static List<Group> groups(List<Earned> written) {
		Map<String, Group> groups = new LinkedHashMap<>();
		for (Earned earned : written) {
			groups.computeIfAbsent(earned.userId() + "|" + earned.examId(), key -> new Group(earned)).add(earned.rate());
		}
		return new ArrayList<>(groups.values());
	}

	/**
	 * The two notices for each group: the Master Admin is owed, the instructor has earned. The key of each includes the run's
	 * batch id, so a retried run cannot repeat a notice, and a second batch on the same day still gets its own count.
	 */
	private void notifyPublished(List<Group> groups, UUID batch, LocalDate today) {
		if (groups.isEmpty()) {
			return;
		}
		List<UUID> masters = masterAdmins();
		for (Group group : groups) {
			BigDecimal balance = StaffPayCalculator.totals(lines(group.userId), today).balance();

			Map<String, Object> owed = new LinkedHashMap<>();
			// Titles allow 500 characters and the notice runs inside the ledger transaction: fit them so a long or
			// number-heavy title cannot roll back the marking lines.
			owed.put("personName", StaffNotificationWriter.fitText(group.fullName));
			owed.put("examTitleEn", StaffNotificationWriter.fitText(group.titleEn));
			owed.put("examTitleBn", StaffNotificationWriter.fitText(group.titleBn));
			owed.put("sheets", group.sheets);
			owed.put("amount", group.amount);
			owed.put("balance", balance);
			for (UUID master : masters) {
				notifications.notify(master, StaffNotificationKind.MARKING_PUBLISHED_OWED, owed, PERSON_LINK + group.userId,
						"MARKING_PUBLISHED_OWED:" + master + ":" + group.userId + ":" + group.examId + ":" + batch);
			}

			Map<String, Object> earned = new LinkedHashMap<>();
			earned.put("examTitleEn", StaffNotificationWriter.fitText(group.titleEn));
			earned.put("examTitleBn", StaffNotificationWriter.fitText(group.titleBn));
			earned.put("sheets", group.sheets);
			earned.put("amount", group.amount);
			notifications.notify(group.userId, StaffNotificationKind.MARKING_PUBLISHED_EARNED, earned, EARNED_LINK,
					"MARKING_PUBLISHED_EARNED:" + group.userId + ":" + group.examId + ":" + batch);
		}
	}

	private List<UUID> masterAdmins() {
		return jdbc.queryForList(MASTER_ADMINS, new MapSqlParameterSource(), UUID.class);
	}

	private List<Line> lines(UUID userId) {
		return jdbc.query(LEDGER_LINES, new MapSqlParameterSource("user", userId), (rs, number) -> new Line(
				rs.getObject("id", UUID.class), LedgerKind.valueOf(rs.getString("kind")), rs.getBigDecimal("amount"),
				rs.getObject("due_on", LocalDate.class), rs.getTimestamp("created_at").toInstant(),
				rs.getTimestamp("voided_at") != null));
	}

	private LocalDate today() {
		return LocalDate.now(clock.withZone(StaffLedgerService.DHAKA));
	}

	/** A published sheet that may earn, as the query reads it. */
	record Candidate(UUID participantId, UUID examId, UUID userId, String evaluatorRole, Instant publishedAt, String status,
			String examType, boolean deleted, String titleEn, String titleBn, String fullName, BigDecimal rate) {
	}

	/** A line written in this run. */
	record Earned(UUID userId, UUID examId, String titleEn, String titleBn, String fullName, BigDecimal rate) {

		Earned(Candidate candidate) {
			this(candidate.userId(), candidate.examId(), candidate.titleEn(), candidate.titleBn(), candidate.fullName(),
					candidate.rate());
		}

	}

	/** The lines of one instructor for one exam, written in this run. */
	private static final class Group {

		private final UUID userId;

		private final UUID examId;

		private final String titleEn;

		private final String titleBn;

		private final String fullName;

		private int sheets;

		private BigDecimal amount = BigDecimal.ZERO;

		Group(Earned first) {
			this.userId = first.userId();
			this.examId = first.examId();
			this.titleEn = first.titleEn();
			this.titleBn = first.titleBn();
			this.fullName = first.fullName();
		}

		void add(BigDecimal rate) {
			sheets++;
			amount = amount.add(rate);
		}

	}

	private record Person(UUID id, String fullName) {
	}

}
