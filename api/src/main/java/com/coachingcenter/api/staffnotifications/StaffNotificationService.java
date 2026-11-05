package com.coachingcenter.api.staffnotifications;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The signed-in staff member's own notifications. Every query is limited to the caller's id, so one person never sees or
 * marks another person's row; a row that is not theirs is reported as not found.
 */
@Service
public class StaffNotificationService {

	static final int DEFAULT_SIZE = 20;

	static final int MAX_SIZE = 50;

	private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

	private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	private static final String SELECT = """
			select n.id, n.kind, n.payload, n.link, n.created_at, n.read_at
			from staff_notifications n
			where n.recipient_id = :me
			""";

	private static final String UNREAD = " and n.read_at is null";

	private final NamedParameterJdbcTemplate jdbc;

	private final ObjectMapper json;

	private final Clock clock;

	public StaffNotificationService(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
		this.json = json;
		this.clock = clock;
	}

	/** One page of the caller's notifications, newest first. {@code unreadOnly} keeps only the unread ones. */
	@Transactional(readOnly = true)
	public Page<NotificationView> list(UUID me, boolean unreadOnly, int page, int size) {
		int pageSize = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
		int pageNumber = Math.max(page, 0);
		String filter = unreadOnly ? UNREAD : "";
		MapSqlParameterSource params = new MapSqlParameterSource("me", me)
			.addValue("limit", pageSize)
			.addValue("offset", (long) pageNumber * pageSize);
		Long total = jdbc.queryForObject("select count(*) from staff_notifications n where n.recipient_id = :me" + filter,
				params, Long.class);
		List<NotificationView> rows = jdbc.query(
				SELECT + filter + " order by n.created_at desc, n.id desc limit :limit offset :offset", params,
				(rs, number) -> view(rs));
		return new PageImpl<>(rows, PageRequest.of(pageNumber, pageSize), total == null ? 0 : total);
	}

	/** How many of the caller's notifications are unread: the bell's number. */
	@Transactional(readOnly = true)
	public UnreadCount unreadCount(UUID me) {
		Long count = jdbc.queryForObject("select count(*) from staff_notifications n where n.recipient_id = :me" + UNREAD,
				new MapSqlParameterSource("me", me), Long.class);
		return new UnreadCount(count == null ? 0 : count.intValue());
	}

	/** Marks one of the caller's notifications read. The first read time is kept. Another person's row is not found. */
	@Transactional
	public NotificationView markRead(UUID me, UUID id) {
		MapSqlParameterSource params = new MapSqlParameterSource("me", me).addValue("id", id)
			.addValue("now", Timestamp.from(clock.instant()));
		int changed = jdbc.update("""
				update staff_notifications set read_at = coalesce(read_at, :now)
				where id = :id and recipient_id = :me
				""", params);
		if (changed == 0) {
			throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
		}
		return jdbc.query(SELECT + " and n.id = :id", params, (rs, number) -> view(rs)).get(0);
	}

	/** Marks every unread notification of the caller read. Returns how many changed. */
	@Transactional
	public int markAllRead(UUID me) {
		return jdbc.update("update staff_notifications set read_at = :now where recipient_id = :me and read_at is null",
				new MapSqlParameterSource("me", me).addValue("now", Timestamp.from(clock.instant())));
	}

	private NotificationView view(ResultSet rs) throws SQLException {
		Timestamp readAt = rs.getTimestamp("read_at");
		return new NotificationView(rs.getObject("id", UUID.class), rs.getString("kind"),
				json.readTree(rs.getString("payload")), rs.getString("link"),
				format(rs.getTimestamp("created_at").toInstant()), readAt == null ? null : format(readAt.toInstant()));
	}

	private static String format(Instant instant) {
		return instant.atZone(DHAKA).format(TIME);
	}

	/**
	 * One notification as the bell and the list show it. {@code kind} is a {@link StaffNotificationKind} name; {@code payload}
	 * holds the values its text needs; {@code readAt} is null while unread.
	 */
	public record NotificationView(UUID id, String kind, JsonNode payload, String link, String createdAt, String readAt) {
	}

	/** The unread count for the bell. */
	public record UnreadCount(int count) {
	}

}
