package com.coachingcenter.api.offline;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The money of offline admissions (plan 4.3, D13): what each due still owes, and the flags the admin pages show. The SQL is written here
 * once, so the queue's filters, the counts, the overdue list and the flags on a queue row all judge a due and a payment the same way.
 * A due owes its amount less the approved payments allocated to it; a cancelled due owes nothing; a payment that is pending, rejected or
 * voided is not money received.
 */
@Component
class OfflineAdmissionMoney {

	static final String NO_PAYMENT = "NO_PAYMENT";

	static final String OVERDUE = "OVERDUE";

	static final String PENDING_APPROVAL = "PENDING_APPROVAL";

	/** One live due (not cancelled) with what is still owed on it. Columns: admission_id, id, seq, due_on, owed. */
	static final String DUE_OWED = """
			select d.id, d.admission_id, d.seq, d.due_on, greatest(d.amount - coalesce(pd.paid, 0), 0) as owed
			from offline_fee_dues d
			left join (
			  select al.due_id, sum(al.amount) as paid
			  from offline_fee_allocations al
			  join offline_fee_payments p on p.id = al.payment_id and p.status = 'APPROVED'
			  group by al.due_id
			) pd on pd.due_id = d.id
			where d.cancelled_at is null
			""";

	private final JdbcTemplate jdbc;

	private final Clock clock;

	OfflineAdmissionMoney(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** Today in Dhaka, the day an overdue due is measured against. */
	LocalDate today() {
		return LocalDate.now(clock.withZone(OfflineFeeRules.DHAKA));
	}

	/**
	 * An SQL predicate on the alias {@code a} (offline_admissions): the admission has a live due that still owes money. With
	 * {@code overdueOnly} the due must also be past its date, which takes one parameter, the day to judge against.
	 */
	static String unpaidDue(boolean overdueOnly) {
		return "exists (select 1 from offline_fee_dues d where d.admission_id = a.id and d.cancelled_at is null"
				+ (overdueOnly ? " and d.due_on < ?" : "")
				+ " and d.amount > coalesce((select sum(al.amount) from offline_fee_allocations al"
				+ " join offline_fee_payments p on p.id = al.payment_id and p.status = 'APPROVED' where al.due_id = d.id), 0))";
	}

	/** An SQL predicate on the alias {@code a}: onboarded, and no approved payment at all. */
	static final String NO_PAYMENT_PREDICATE = "(a.status = 'ONBOARDED' and not exists (select 1 from offline_fee_payments p"
			+ " where p.admission_id = a.id and p.status = 'APPROVED'))";

	/** An SQL predicate on the alias {@code a}: a staff payment waits for approval. */
	static final String PENDING_PREDICATE = "exists (select 1 from offline_fee_payments p where p.admission_id = a.id"
			+ " and p.status = 'PENDING')";

	/** One unpaid due of a page, read from {@link #DUE_OWED}. */
	private record UnpaidDue(UUID admissionId, LocalDate dueOn, BigDecimal owed) {
	}

	/** How many approved and how many pending payments one admission has. */
	private record PaymentCount(UUID admissionId, long approved, long pending) {
	}

	/** The money of one admission, as a page of the queue shows it. */
	record Money(BigDecimal owedTotal, LocalDate nextDueOn, long overdueDues, long approvedPayments, long pendingPayments) {

		static final Money NONE = new Money(BigDecimal.ZERO, null, 0L, 0L, 0L);

		/** The flags a queue row carries, in a fixed order (plan 4.3). */
		List<String> flags(String status) {
			List<String> flags = new ArrayList<>();
			if (OfflineAdmission.ONBOARDED.equals(status) && approvedPayments == 0) {
				flags.add(NO_PAYMENT);
			}
			if (overdueDues > 0) {
				flags.add(OVERDUE);
			}
			if (pendingPayments > 0) {
				flags.add(PENDING_APPROVAL);
			}
			return flags;
		}

	}

	/**
	 * The money of every admission on one page, keyed by admission id. Two queries for the whole page, never one per row: the unpaid
	 * dues and the payment counts of the page's ids. An admission with nothing to show is absent from the map (read it as {@link Money#NONE}).
	 */
	Map<UUID, Money> page(List<UUID> admissionIds, LocalDate today) {
		if (admissionIds.isEmpty()) {
			return Map.of();
		}
		String in = String.join(",", Collections.nCopies(admissionIds.size(), "?"));
		Object[] ids = admissionIds.toArray();
		List<UnpaidDue> unpaid = jdbc.query("select x.admission_id, x.due_on, x.owed from (" + DUE_OWED + ") x"
				+ " where x.admission_id in (" + in + ") and x.owed > 0", (rs, row) -> new UnpaidDue(rs.getObject("admission_id", UUID.class),
				rs.getDate("due_on").toLocalDate(), rs.getBigDecimal("owed")), ids);
		Map<UUID, BigDecimal> owed = new HashMap<>();
		Map<UUID, LocalDate> nextDue = new HashMap<>();
		Map<UUID, Long> overdue = new HashMap<>();
		for (UnpaidDue due : unpaid) {
			owed.merge(due.admissionId(), due.owed(), BigDecimal::add);
			nextDue.merge(due.admissionId(), due.dueOn(), (left, right) -> left.isBefore(right) ? left : right);
			if (due.dueOn().isBefore(today)) {
				overdue.merge(due.admissionId(), 1L, Long::sum);
			}
		}
		List<PaymentCount> counts = jdbc.query("""
				select admission_id, count(*) filter (where status = 'APPROVED') as approved,
				  count(*) filter (where status = 'PENDING') as pending
				from offline_fee_payments where admission_id in (""" + in + ") group by admission_id",
				(rs, row) -> new PaymentCount(rs.getObject("admission_id", UUID.class), rs.getLong("approved"), rs.getLong("pending")),
				ids);
		Map<UUID, PaymentCount> payments = new HashMap<>();
		for (PaymentCount count : counts) {
			payments.put(count.admissionId(), count);
		}
		Map<UUID, Money> money = new HashMap<>();
		for (UUID id : admissionIds) {
			PaymentCount paid = payments.get(id);
			money.put(id, new Money(owed.getOrDefault(id, BigDecimal.ZERO), nextDue.get(id), overdue.getOrDefault(id, 0L),
					paid == null ? 0L : paid.approved(), paid == null ? 0L : paid.pending()));
		}
		return money;
	}

}
