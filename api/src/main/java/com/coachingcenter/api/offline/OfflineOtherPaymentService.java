package com.coachingcenter.api.offline;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;

import tools.jackson.databind.JsonNode;

/**
 * Payments to an offline instructor for something that is not a class (17 Nov): travel, a bonus, an advance. The admin types the reason.
 * A batch is optional; with one, Finance counts the payment in that batch's instructor cost. As with class payments, a staff entry waits
 * for the Master Admin (PENDING) and the Master Admin's own counts at once (APPROVED). Finance and the payment records page read the
 * approved rows.
 */
@Service
public class OfflineOtherPaymentService {

	public static final String ENTITY = "OFFLINE_OTHER_PAYMENT";

	public static final String RECORDED = "OFFLINE_OTHER_PAYMENT_RECORDED";

	public static final String APPROVED = "OFFLINE_OTHER_PAYMENT_APPROVED";

	public static final String REJECTED = "OFFLINE_OTHER_PAYMENT_REJECTED";

	public static final String VOIDED = "OFFLINE_OTHER_PAYMENT_VOIDED";

	private static final Set<String> METHODS = Set.of("CASH", "BKASH", "NAGAD", "ROCKET", "BANK");

	private static final Pattern REFERENCE = Pattern.compile("[ -~]{1,100}");

	private static final int MAX_TEXT = 500;

	private static final String SELECT = """
			select p.*, i.name as instructor_name, b.name_en as batch_name_en, b.name_bn as batch_name_bn,
			  coalesce(ap.full_name, '') as recorded_by_name
			from offline_instructor_other_payments p
			join offline_instructors i on i.id = p.instructor_id
			left join offline_batches b on b.id = p.batch_id
			left join admin_profiles ap on ap.user_id = p.recorded_by
			""";

	/** One payment as the screens show it. */
	public record OtherPaymentView(UUID id, UUID instructorId, String instructorName, UUID batchId, String batchNameEn, String batchNameBn,
			String reason, BigDecimal amount, String method, String reference, LocalDate paidOn, String note, String status,
			String recordedByName, Instant recordedAt, String decisionReason, Instant correctedAt) {
	}

	private final JdbcTemplate jdbc;

	private final AuditRecorder audit;

	private final Clock clock;

	public OfflineOtherPaymentService(JdbcTemplate jdbc, AuditRecorder audit, Clock clock) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
	}

	/** The instructor's other payments, newest paid first. */
	@Transactional(readOnly = true)
	public List<OtherPaymentView> forInstructor(UUID instructorId) {
		requireInstructor(instructorId);
		return jdbc.query(SELECT + " where p.instructor_id = ? order by p.paid_on desc, p.recorded_at desc", (rs, row) -> view(rs),
				instructorId);
	}

	/** Every other payment in one status (PENDING for the waiting tab), newest first. */
	@Transactional(readOnly = true)
	public List<OtherPaymentView> inStatus(String status) {
		String wanted = status == null || status.isBlank() ? "PENDING" : status.trim().toUpperCase(Locale.ROOT);
		return jdbc.query(SELECT + " where p.status = ? order by p.recorded_at desc limit 500", (rs, row) -> view(rs), wanted);
	}

	/** Records a payment. {@code master} is the Master Admin, whose payment counts at once; a staff one waits. */
	@Transactional
	public OtherPaymentView record(UUID instructorId, UUID actorId, boolean master, JsonNode body) {
		requireInstructor(instructorId);
		List<FieldErrorDetail> problems = new ArrayList<>();
		String reason = text(body, "reason");
		if (reason == null) {
			problems.add(new FieldErrorDetail("reason", "Say what the payment is for."));
		}
		else if (reason.length() > MAX_TEXT) {
			problems.add(new FieldErrorDetail("reason", "The reason is at most 500 characters."));
		}
		BigDecimal amount = null;
		try {
			String raw = text(body, "amount");
			amount = raw == null ? null : new BigDecimal(raw);
		}
		catch (NumberFormatException invalid) {
			amount = null;
		}
		if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
			problems.add(new FieldErrorDetail("amount", "Enter an amount above zero, with at most two decimals."));
		}
		LocalDate paidOn = null;
		try {
			String raw = text(body, "paidOn");
			paidOn = raw == null ? null : LocalDate.parse(raw);
		}
		catch (java.time.format.DateTimeParseException invalid) {
			paidOn = null;
		}
		if (paidOn == null) {
			problems.add(new FieldErrorDetail("paidOn", "Enter the date it was paid."));
		}
		else if (paidOn.isAfter(OfflineFeeSchedule.today(clock.instant()))) {
			problems.add(new FieldErrorDetail("paidOn", "The payment date cannot be in the future."));
		}
		String method = text(body, "method") == null ? "" : text(body, "method").toUpperCase(Locale.ROOT);
		if (!METHODS.contains(method)) {
			problems.add(new FieldErrorDetail("method", "Choose a payment method."));
		}
		String reference = text(body, "reference");
		if (reference != null && !REFERENCE.matcher(reference).matches()) {
			problems.add(new FieldErrorDetail("reference", "A reference is 1 to 100 printable characters, no accents."));
		}
		String note = text(body, "note");
		if (note != null && note.length() > MAX_TEXT) {
			problems.add(new FieldErrorDetail("note", "The note is at most 500 characters."));
		}
		UUID batchId = null;
		String batchText = text(body, "batchId");
		if (batchText != null) {
			try {
				batchId = UUID.fromString(batchText);
				if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from offline_batches where id = ?)", Boolean.class, batchId))) {
					problems.add(new FieldErrorDetail("batchId", "Choose a batch from the list."));
				}
			}
			catch (IllegalArgumentException invalid) {
				problems.add(new FieldErrorDetail("batchId", "Choose a batch from the list."));
			}
		}
		if (!problems.isEmpty()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.", problems);
		}
		UUID id = UUID.randomUUID();
		Instant now = clock.instant();
		jdbc.update("""
				insert into offline_instructor_other_payments (id, instructor_id, batch_id, reason, amount, method, reference, paid_on, note,
				  status, recorded_by, recorded_at, decided_by, decided_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", id, instructorId, batchId, reason, amount.setScale(2, RoundingMode.HALF_UP), method, reference, Date.valueOf(paidOn), note,
				master ? "APPROVED" : "PENDING", actorId, Timestamp.from(now), master ? actorId : null, master ? Timestamp.from(now) : null);
		audit.record(RECORDED, ENTITY, id);
		return find(id);
	}

	/** The Master Admin approves a waiting payment. */
	@Transactional
	public OtherPaymentView approve(UUID id, UUID actorId) {
		decide(id, actorId, "PENDING", "APPROVED", null);
		audit.record(APPROVED, ENTITY, id);
		return find(id);
	}

	/** The Master Admin rejects a waiting payment, with a reason the recorder reads. */
	@Transactional
	public OtherPaymentView reject(UUID id, UUID actorId, JsonNode body) {
		decide(id, actorId, "PENDING", "REJECTED", requiredReason(body));
		audit.record(REJECTED, ENTITY, id);
		return find(id);
	}

	/** The Master Admin voids an approved payment, with a reason. It leaves Finance. */
	@Transactional
	public OtherPaymentView voidPayment(UUID id, UUID actorId, JsonNode body) {
		String reason = requiredReason(body);
		String status = jdbc.queryForList("select status from offline_instructor_other_payments where id = ? for update", String.class, id)
			.stream().findFirst().orElseThrow(OfflineSupport::notFound);
		if (!"APPROVED".equals(status)) {
			throw new ApiException(ErrorCode.ADMISSION_STATE, HttpStatus.CONFLICT, "Only an approved payment can be voided.");
		}
		jdbc.update("update offline_instructor_other_payments set status = 'VOIDED', voided_by = ?, voided_at = ?, void_reason = ? where id = ?",
				actorId, Timestamp.from(clock.instant()), reason, id);
		audit.record(VOIDED, ENTITY, id);
		return find(id);
	}

	private void decide(UUID id, UUID actorId, String from, String to, String reason) {
		String status = jdbc.queryForList("select status from offline_instructor_other_payments where id = ? for update", String.class, id)
			.stream().findFirst().orElseThrow(OfflineSupport::notFound);
		if (!from.equals(status)) {
			throw new ApiException(ErrorCode.ADMISSION_STATE, HttpStatus.CONFLICT, "This payment is not waiting for approval.");
		}
		jdbc.update("update offline_instructor_other_payments set status = ?, decided_by = ?, decided_at = ?, decision_reason = ? where id = ?",
				to, actorId, Timestamp.from(clock.instant()), reason, id);
	}

	private static String requiredReason(JsonNode body) {
		String reason = text(body, "reason");
		if (reason == null) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
					List.of(new FieldErrorDetail("reason", "Give a reason.")));
		}
		return reason.length() > MAX_TEXT ? reason.substring(0, MAX_TEXT) : reason;
	}

	private OtherPaymentView find(UUID id) {
		return jdbc.query(SELECT + " where p.id = ?", (rs, row) -> view(rs), id).stream().findFirst().orElseThrow(OfflineSupport::notFound);
	}

	private void requireInstructor(UUID instructorId) {
		if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from offline_instructors where id = ?)", Boolean.class,
				instructorId))) {
			throw OfflineSupport.notFound();
		}
	}

	private static OtherPaymentView view(ResultSet rs) throws SQLException {
		Timestamp corrected = rs.getTimestamp("corrected_at");
		return new OtherPaymentView(rs.getObject("id", UUID.class), rs.getObject("instructor_id", UUID.class), rs.getString("instructor_name"),
				rs.getObject("batch_id", UUID.class), rs.getString("batch_name_en"), rs.getString("batch_name_bn"), rs.getString("reason"),
				rs.getBigDecimal("amount"), rs.getString("method"), rs.getString("reference"), rs.getDate("paid_on").toLocalDate(),
				rs.getString("note"), rs.getString("status"), rs.getString("recorded_by_name"), rs.getTimestamp("recorded_at").toInstant(),
				rs.getString("decision_reason"), corrected == null ? null : corrected.toInstant());
	}

	private static String text(JsonNode body, String key) {
		if (body == null || !body.hasNonNull(key)) {
			return null;
		}
		String value = body.get(key).asText().trim();
		return value.isEmpty() ? null : value;
	}

}
