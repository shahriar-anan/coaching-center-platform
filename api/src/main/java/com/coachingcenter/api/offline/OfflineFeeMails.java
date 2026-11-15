package com.coachingcenter.api.offline;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.common.config.AppProperties;
import com.coachingcenter.api.common.mail.EmailTemplates;
import com.coachingcenter.api.common.mail.MailContext;
import com.coachingcenter.api.common.mail.MailRecipient;
import com.coachingcenter.api.common.mail.RenderedEmail;

/**
 * Turns a queued monthly fee mail into its text when it is sent (plan section 7). The mail outbox calls {@link #render} through
 * {@code ExamMailTexts} for the four fee kinds. Each render checks the row again: the payment must still be approved, the due must
 * still owe money, and the student must still be active with a verified address. Otherwise nothing is sent and the row is skipped.
 */
@Component
public class OfflineFeeMails {

	/** The mail kinds this renderer handles. They are the kinds the outbox constraint allows (V84). */
	public static final Set<String> KINDS = Set.of(OfflineFeeNotices.MAIL_RECEIPT, OfflineFeeNotices.DUE_SOON,
			OfflineFeeNotices.DUE_TODAY, OfflineFeeNotices.OVERDUE);

	/** A prepared mail: where it goes, its subject, and its two bodies. */
	public record Mail(String to, String subject, String text, String html) {
	}

	private static final String ACTIVE_VERIFIED_STUDENT = """
			select u.email, coalesce(sp.full_name, '') as name from users u
			left join student_profiles sp on sp.user_id = u.id
			where u.id = ? and u.role = 'STUDENT' and u.status = 'ACTIVE' and u.deleted_at is null
			  and u.email is not null and u.email <> ''
			""";

	private final JdbcTemplate jdbc;

	private final OfflineFeeLedger ledger;

	private final MailContext context;

	public OfflineFeeMails(JdbcTemplate jdbc, OfflineFeeLedger ledger, AppProperties properties) {
		this.jdbc = jdbc;
		this.ledger = ledger;
		this.context = MailContext.from(properties);
	}

	public static boolean handles(String kind) {
		return KINDS.contains(kind);
	}

	/** The mail for one outbox row, or null when nothing should be sent any more. */
	public Mail render(String kind, String key, UUID userId) {
		if (!handles(kind) || key == null || userId == null) {
			return null;
		}
		UUID id;
		try {
			id = UUID.fromString(key);
		}
		catch (IllegalArgumentException exception) {
			return null;
		}
		return OfflineFeeNotices.MAIL_RECEIPT.equals(kind) ? receipt(id, userId) : reminder(kind, id, userId);
	}

	private Mail receipt(UUID paymentId, UUID userId) {
		Optional<OfflineFeeLedger.Payment> found = ledger.payment(paymentId);
		if (found.isEmpty() || !OfflineFeePayment.STATUS_APPROVED.equals(found.get().status())
				|| !userId.equals(found.get().studentId())) {
			return null;
		}
		OfflineFeeLedger.Payment payment = found.get();
		Recipient to = recipient(userId);
		if (to == null) {
			return null;
		}
		BigDecimal stillOwed = ledger.owed(payment.admissionId());
		RenderedEmail mail = EmailTemplates.offlineFeeReceipt(context, new MailRecipient(to.email(), to.name(), Role.STUDENT),
				payment.amount().toPlainString(), OfflineFeeRules.methodLabel(payment.method()), payment.reference(),
				payment.paidOn().toString(), payment.receiptNo(), stillOwed.toPlainString(), context.site("/my-admission"));
		return new Mail(to.email(), mail.subject(), mail.text(), mail.html());
	}

	private Mail reminder(String kind, UUID dueId, UUID userId) {
		List<Row> rows = jdbc.query("""
				select d.amount - coalesce(sum(a.amount) filter (where p.status = 'APPROVED'), 0) as owed, d.due_on,
				  a2.student_id
				from offline_fee_dues d
				join offline_admissions a2 on a2.id = d.admission_id
				left join offline_fee_allocations a on a.due_id = d.id
				left join offline_fee_payments p on p.id = a.payment_id
				where d.id = ? and d.cancelled_at is null
				group by d.id, d.amount, d.due_on, a2.student_id
				""", (rs, row) -> new Row(rs.getBigDecimal("owed"), rs.getDate("due_on").toLocalDate(),
						rs.getObject("student_id", UUID.class)), dueId);
		if (rows.isEmpty() || rows.get(0).owed().signum() <= 0 || !userId.equals(rows.get(0).studentId())) {
			return null;
		}
		Row due = rows.get(0);
		Recipient to = recipient(userId);
		if (to == null) {
			return null;
		}
		String stage = switch (kind) {
			case OfflineFeeNotices.DUE_SOON -> "DUE_SOON";
			case OfflineFeeNotices.DUE_TODAY -> "DUE_TODAY";
			default -> "OVERDUE";
		};
		RenderedEmail mail = EmailTemplates.offlineFeeReminder(context, new MailRecipient(to.email(), to.name(), Role.STUDENT),
				stage, due.owed().toPlainString(), due.dueOn().toString(), context.site("/my-admission"));
		return new Mail(to.email(), mail.subject(), mail.text(), mail.html());
	}

	private Recipient recipient(UUID userId) {
		List<Recipient> found = jdbc.query(ACTIVE_VERIFIED_STUDENT, (rs, row) -> new Recipient(rs.getString("email"),
				rs.getString("name")), userId);
		return found.isEmpty() ? null : found.get(0);
	}

	private record Recipient(String email, String name) {
	}

	private record Row(BigDecimal owed, LocalDate dueOn, UUID studentId) {
	}

}
