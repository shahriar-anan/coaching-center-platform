package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A student's own exam orders, newest first. Status uses the public words of {@code /live-exam-orders/status}, and the
 * row carries no {@code valId} or contact details.
 */
@Service
public class LiveExamOrderHistoryService {

	private final NamedParameterJdbcTemplate jdbc;

	LiveExamOrderHistoryService(JdbcTemplate jdbc) {
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
	}

	@Transactional(readOnly = true)
	public Page<OwnOrderRow> list(UUID studentId, Pageable pageable) {
		int size = Math.min(Math.max(pageable.getPageSize(), 1), LiveExamOrderAdminService.MAX_SIZE);
		Pageable page = PageRequest.of(pageable.getPageNumber(), size);
		MapSqlParameterSource params = new MapSqlParameterSource("studentId", studentId);
		Long total = jdbc.queryForObject("select count(*) from live_exam_orders where student_id = :studentId", params,
				Long.class);
		params.addValue("limit", size).addValue("offset", (long) page.getPageNumber() * size);
		List<OwnOrderRow> rows = jdbc.query("""
				select o.id, o.tran_id, o.status, o.amount, o.currency, o.scope, o.module_id, o.exam_id, o.payment_channel,
				  o.created_at, m.title_en as module_title_en, m.title_bn as module_title_bn,
				  e.title_en as exam_title_en, e.title_bn as exam_title_bn
				from live_exam_orders o
				left join live_exam_modules m on m.id = o.module_id
				left join live_exams e on e.id = o.exam_id
				where o.student_id = :studentId
				order by o.created_at desc, o.id
				limit :limit offset :offset
				""", params,
				(rs, number) -> new OwnOrderRow(rs.getObject("id", UUID.class), rs.getString("tran_id"),
						LiveExamOrderService.publicStatus(rs.getString("status")), rs.getBigDecimal("amount"),
						rs.getString("currency"), rs.getString("scope"), rs.getObject("module_id", UUID.class),
						rs.getString("module_title_en"), rs.getString("module_title_bn"), rs.getObject("exam_id", UUID.class),
						rs.getString("exam_title_en"), rs.getString("exam_title_bn"), rs.getString("payment_channel"),
						instant(rs.getTimestamp("created_at"))));
		return new PageImpl<>(rows, page, total == null ? 0 : total);
	}

	private static Instant instant(Timestamp value) {
		return value == null ? null : value.toInstant();
	}

	public record OwnOrderRow(UUID orderId, String tranId, String status, BigDecimal amount, String currency, String scope,
			UUID moduleId, String moduleTitleEn, String moduleTitleBn, UUID examId, String examTitleEn, String examTitleBn,
			String paymentChannel, Instant createdAt) {
	}

}
