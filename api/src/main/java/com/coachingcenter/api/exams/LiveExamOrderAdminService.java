package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
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
 * Read-only staff list of exam orders, so a payment can be found without the database or the SSLCommerz panel.
 * {@code accessGranted} counts only the grant this order wrote. Roster access is separate. A bundle row
 * ({@code scope = BUNDLE}) has no exam id: it carries the module's titles, and {@code accessGranted} is true when any
 * of its exam grants is active.
 */
@Service
public class LiveExamOrderAdminService {

	static final int MAX_SIZE = 100;

	private static final Set<String> STATUSES = Set.of(LiveExamOrder.INITIATED, LiveExamOrder.SUCCEEDED,
			LiveExamOrder.FAILED, LiveExamOrder.CANCELLED);

	private static final String FROM = """
			from live_exam_orders o
			join users u on u.id = o.student_id
			left join student_profiles sp on sp.user_id = u.id
			left join live_exams e on e.id = o.exam_id
			left join lateral (select p.module_id from live_exam_package_exams p
			  join live_exam_modules pm on pm.id = p.module_id and pm.deleted_at is null
			  where p.exam_id = o.exam_id and p.removed_at is null
			  order by p.added_at, p.id limit 1) pk on true
			left join live_exam_modules m on m.id = coalesce(o.module_id, pk.module_id)
			where (:status = '' or o.status = :status)
			  and (:tranId = '' or o.tran_id = :tranId)
			  and (:q = '' or u.phone ilike concat('%', :q, '%') or u.email ilike concat('%', :q, '%')
			       or sp.full_name ilike concat('%', :q, '%') or sp.student_code ilike concat('%', :q, '%'))
			""";

	private final NamedParameterJdbcTemplate jdbc;

	LiveExamOrderAdminService(JdbcTemplate jdbc) {
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
	}

	@Transactional(readOnly = true)
	public Page<OrderRow> list(String status, String tranId, String q, Pageable pageable) {
		String state = status == null ? "" : status.trim().toUpperCase(java.util.Locale.ROOT);
		if (!state.isEmpty() && !STATUSES.contains(state)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
		MapSqlParameterSource params = new MapSqlParameterSource("status", state)
			.addValue("tranId", tranId == null ? "" : tranId.trim())
			.addValue("q", q == null ? "" : q.trim());
		int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_SIZE);
		Pageable page = PageRequest.of(pageable.getPageNumber(), size);
		Long total = jdbc.queryForObject("select count(*) " + FROM, params, Long.class);
		params.addValue("limit", size).addValue("offset", (long) page.getPageNumber() * size);
		String sql = """
				select o.id, o.tran_id, o.status, o.amount, o.currency, o.scope, coalesce(o.module_id, pk.module_id) as module_id,
				  o.exam_id, o.payment_channel, o.list_price, o.discount_percent,
				  o.val_id, o.created_at, o.updated_at, m.title_en as module_title_en, m.title_bn as module_title_bn,
				  e.title_en as exam_title_en, e.title_bn as exam_title_bn, u.id as student_id, u.phone, u.email,
				  sp.full_name, sp.student_code,
				  exists (select 1 from live_exam_grants g where g.order_id = o.id and g.revoked_at is null) as access_granted
				%s
				order by o.created_at desc, o.id
				limit :limit offset :offset
				""".formatted(FROM);
		List<OrderRow> rows = jdbc.query(sql, params,
				(rs, number) -> new OrderRow(rs.getObject("id", UUID.class), rs.getString("tran_id"), rs.getString("status"),
						rs.getBigDecimal("amount"), rs.getString("currency"), rs.getString("scope"),
						rs.getObject("module_id", UUID.class), rs.getString("module_title_en"), rs.getString("module_title_bn"),
						rs.getObject("exam_id", UUID.class), rs.getString("exam_title_en"), rs.getString("exam_title_bn"),
						rs.getString("payment_channel"), rs.getString("val_id"), rs.getBoolean("access_granted"),
						rs.getObject("student_id", UUID.class), rs.getString("full_name"), rs.getString("phone"),
						rs.getString("email"), rs.getString("student_code"), instant(rs.getTimestamp("created_at")),
						instant(rs.getTimestamp("updated_at")), rs.getBigDecimal("list_price"),
						rs.getObject("discount_percent", Integer.class)));
		return new PageImpl<>(rows, page, total == null ? 0 : total);
	}

	private static Instant instant(Timestamp value) {
		return value == null ? null : value.toInstant();
	}

	public record OrderRow(UUID orderId, String tranId, String status, BigDecimal amount, String currency, String scope,
			UUID moduleId, String moduleTitleEn, String moduleTitleBn, UUID examId, String examTitleEn, String examTitleBn,
			String paymentChannel, String valId, boolean accessGranted, UUID studentId, String studentName,
			String studentPhone, String studentEmail, String studentCode, Instant createdAt, Instant updatedAt,
			BigDecimal listPrice, Integer discountPercent) {
	}

}
