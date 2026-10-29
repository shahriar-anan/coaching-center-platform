package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * A student's own exam orders, newest first. Status uses the public words of {@code /live-exam-orders/status}, and the
 * row carries no {@code valId} or contact details. A bundle row ({@code scope = BUNDLE}) has no exam id and carries
 * the module's titles; its exams are on {@code GET /live-exam-orders/{id}} as {@code examIds}.
 *
 * <p>The list takes optional filters (P1): status words, scope, one package, one exam (which also matches a bundle that
 * holds it), a date range in Bangladesh time, and one order id (P3). A filter that is not sent is left out of the SQL.
 * The date range looks at the paid time, or at the created time for an order not yet paid.
 */
@Service
public class LiveExamOrderHistoryService {

	/** The public status word a filter may name, and the stored status it matches (P1). */
	private static final Map<String, String> STORED_STATUS = Map.of("PENDING", LiveExamOrder.INITIATED, "PAID",
			LiveExamOrder.SUCCEEDED, "FAILED", LiveExamOrder.FAILED, "CANCELLED", LiveExamOrder.CANCELLED);

	private static final List<String> SCOPES = List.of(LiveExamOrder.MODULE, LiveExamOrder.EXAM, LiveExamOrder.BUNDLE);

	private static final String FROM = """
			from live_exam_orders o
			left join live_exams e on e.id = o.exam_id
			left join lateral (select p.module_id from live_exam_package_exams p
			  join live_exam_modules pm on pm.id = p.module_id and pm.deleted_at is null
			  where p.exam_id = o.exam_id and p.removed_at is null
			  order by p.added_at, p.id limit 1) pk on true
			left join live_exam_modules m on m.id = coalesce(o.module_id, pk.module_id)
			where o.student_id = :studentId
			""";

	private static final String SELECT = """
			select o.id, o.tran_id, o.status, o.amount, o.currency, o.scope, coalesce(o.module_id, pk.module_id) as module_id,
			  o.exam_id, o.payment_channel, o.list_price, o.discount_percent,
			  o.created_at, o.paid_at, o.receipt_no, o.receipt_email, m.title_en as module_title_en, m.title_bn as module_title_bn,
			  e.title_en as exam_title_en, e.title_bn as exam_title_bn
			""";

	private final NamedParameterJdbcTemplate jdbc;

	LiveExamOrderHistoryService(JdbcTemplate jdbc) {
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
	}

	@Transactional(readOnly = true)
	public Page<OwnOrderRow> list(UUID studentId, OrderFilter filter, Pageable pageable) {
		int size = Math.min(Math.max(pageable.getPageSize(), 1), LiveExamOrderAdminService.MAX_SIZE);
		Pageable page = PageRequest.of(pageable.getPageNumber(), size);
		MapSqlParameterSource params = new MapSqlParameterSource("studentId", studentId);
		String where = conditions(filter, params);
		Long total = jdbc.queryForObject("select count(*) " + FROM + where, params, Long.class);
		params.addValue("limit", size).addValue("offset", (long) page.getPageNumber() * size);
		List<OwnOrderRow> rows = jdbc.query(SELECT + FROM + where + " order by o.created_at desc, o.id limit :limit offset :offset",
				params, (rs, number) -> new OwnOrderRow(rs.getObject("id", UUID.class), rs.getString("tran_id"),
						LiveExamOrderService.publicStatus(rs.getString("status")), rs.getBigDecimal("amount"),
						rs.getString("currency"), rs.getString("scope"), rs.getObject("module_id", UUID.class),
						rs.getString("module_title_en"), rs.getString("module_title_bn"), rs.getObject("exam_id", UUID.class),
						rs.getString("exam_title_en"), rs.getString("exam_title_bn"), rs.getString("payment_channel"),
						instant(rs.getTimestamp("created_at")), rs.getBigDecimal("list_price"),
						rs.getObject("discount_percent", Integer.class), instant(rs.getTimestamp("paid_at")),
						rs.getString("receipt_no"), rs.getString("receipt_email")));
		return new PageImpl<>(rows, page, total == null ? 0 : total);
	}

	/** Totals for the summary strip (P1): what the student has paid, and how many orders are paid or awaiting payment. */
	@Transactional(readOnly = true)
	public OrderSummary summary(UUID studentId) {
		return jdbc.queryForObject("""
				select coalesce(sum(o.amount) filter (where o.status = 'SUCCEEDED'), 0) as total_paid,
				  count(*) filter (where o.status = 'SUCCEEDED') as paid_count,
				  count(*) filter (where o.status = 'INITIATED') as pending_count
				from live_exam_orders o where o.student_id = :studentId
				""", new MapSqlParameterSource("studentId", studentId),
				(rs, number) -> new OrderSummary(rs.getBigDecimal("total_paid"), LiveExamOrder.BDT,
						rs.getInt("paid_count"), rs.getInt("pending_count")));
	}

	/** One paid order of the student, for the receipt PDF (P4). Any other order is 404, and so is another student's. */
	@Transactional(readOnly = true)
	public Receipt receipt(UUID studentId, UUID orderId) {
		List<OwnOrderRow> rows = list(studentId, new OrderFilter(null, null, null, null, null, null, orderId),
				PageRequest.of(0, 1)).getContent();
		if (rows.isEmpty() || !"PAID".equals(rows.get(0).status()) || rows.get(0).receiptNo() == null) {
			throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
		}
		List<String[]> names = jdbc.query("""
				select sp.full_name, u.phone from users u
				left join student_profiles sp on sp.user_id = u.id
				where u.id = :studentId
				""", new MapSqlParameterSource("studentId", studentId),
				(rs, number) -> new String[] { rs.getString("full_name"), rs.getString("phone") });
		String[] who = names.isEmpty() ? new String[2] : names.get(0);
		return new Receipt(rows.get(0), who[0], who[1]);
	}

	/**
	 * One SQL condition per filter that is set, each starting with {@code and}, appended after the student's own orders.
	 */
	private static String conditions(OrderFilter filter, MapSqlParameterSource params) {
		if (filter == null) {
			return "";
		}
		StringBuilder sql = new StringBuilder();
		if (filter.statuses() != null && !filter.statuses().isEmpty()) {
			List<String> stored = new ArrayList<>();
			for (String word : filter.statuses()) {
				stored.add(storedStatus(word));
			}
			sql.append(" and o.status in (:statuses)");
			params.addValue("statuses", stored);
		}
		if (filter.scope() != null) {
			sql.append(" and o.scope = :scope");
			params.addValue("scope", scope(filter.scope()));
		}
		if (filter.moduleId() != null) {
			sql.append(" and coalesce(o.module_id, pk.module_id) = :moduleId");
			params.addValue("moduleId", filter.moduleId());
		}
		if (filter.examId() != null) {
			sql.append(" and (o.exam_id = :examId or (o.scope = 'BUNDLE' and exists (select 1 from live_exam_order_exams x"
					+ " where x.order_id = o.id and x.exam_id = :examId)))");
			params.addValue("examId", filter.examId());
		}
		if (filter.from() != null && filter.to() != null && filter.to().isBefore(filter.from())) {
			throw validationFailed();
		}
		if (filter.from() != null) {
			sql.append(" and coalesce(o.paid_at, o.created_at) >= :from");
			params.addValue("from", Timestamp.from(startOfDay(filter.from())));
		}
		if (filter.to() != null) {
			sql.append(" and coalesce(o.paid_at, o.created_at) < :toExclusive");
			params.addValue("toExclusive", Timestamp.from(startOfDay(filter.to().plusDays(1))));
		}
		if (filter.orderId() != null) {
			sql.append(" and o.id = :orderId");
			params.addValue("orderId", filter.orderId());
		}
		return sql.toString();
	}

	private static String storedStatus(String word) {
		String stored = word == null ? null : STORED_STATUS.get(word.trim().toUpperCase(Locale.ROOT));
		if (stored == null) {
			throw validationFailed();
		}
		return stored;
	}

	private static String scope(String value) {
		String scope = value.trim().toUpperCase(Locale.ROOT);
		if (!SCOPES.contains(scope)) {
			throw validationFailed();
		}
		return scope;
	}

	/** Midnight at the start of a Bangladesh day. */
	private static Instant startOfDay(LocalDate day) {
		return day.atStartOfDay(LiveExamReceipts.DHAKA).toInstant();
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private static Instant instant(Timestamp value) {
		return value == null ? null : value.toInstant();
	}

	/**
	 * The list filters (P1, P3). Every field is optional. {@code statuses} takes the public words PENDING, PAID, FAILED and
	 * CANCELLED; {@code scope} takes MODULE, EXAM or BUNDLE.
	 */
	public record OrderFilter(List<String> statuses, String scope, UUID moduleId, UUID examId, LocalDate from, LocalDate to,
			UUID orderId) {
	}

	public record OrderSummary(BigDecimal totalPaid, String currency, int paidCount, int pendingCount) {
	}

	public record OwnOrderRow(UUID orderId, String tranId, String status, BigDecimal amount, String currency, String scope,
			UUID moduleId, String moduleTitleEn, String moduleTitleBn, UUID examId, String examTitleEn, String examTitleBn,
			String paymentChannel, Instant createdAt, BigDecimal listPrice, Integer discountPercent, Instant paidAt,
			String receiptNo, String receiptEmail) {
	}

	/** A paid order with the student's name and phone, as the receipt prints them. Either may be null. */
	public record Receipt(OwnOrderRow order, String studentName, String studentPhone) {
	}

}
