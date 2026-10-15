package com.coachingcenter.api.courses;

import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import tools.jackson.databind.JsonNode;

@Service
public class CourseRoutineService {

	private final CourseRepository courses;

	private final CourseRoutineSlotRepository slots;

	public CourseRoutineService(CourseRepository courses, CourseRoutineSlotRepository slots) {
		this.courses = courses;
		this.slots = slots;
	}

	@Transactional
	public List<SlotView> replace(UUID courseId, JsonNode body) {
		Course course = requireCourse(courseId);
		if (body == null || !body.has("slots") || !body.get("slots").isArray()) {
			throw validationFailed();
		}
		List<ParsedSlot> parsed = new ArrayList<>();
		for (JsonNode node : body.get("slots")) {
			parsed.add(parse(node));
		}
		if (!parsed.isEmpty() && !acceptsRoutine(course.getDeliveryMode())) {
			throw conflict(ErrorCode.ROUTINE_NOT_APPLICABLE, "A routine applies only to an offline or hybrid course.");
		}
		rejectOverlaps(parsed);
		Instant now = Instant.now();
		for (CourseRoutineSlot existing : slots.findByCourseIdAndDeletedAtIsNullOrderByPositionAsc(courseId)) {
			existing.setDeletedAt(now);
		}
		for (int index = 0; index < parsed.size(); index++) {
			ParsedSlot slot = parsed.get(index);
			CourseRoutineSlot row = new CourseRoutineSlot();
			row.setCourseId(courseId);
			row.setWeekday(slot.weekday());
			row.setStartTime(slot.startTime());
			row.setEndTime(slot.endTime());
			row.setPlace(slot.place());
			row.setPosition(index);
			slots.save(row);
		}
		return list(courseId);
	}

	@Transactional(readOnly = true)
	public List<SlotView> list(UUID courseId) {
		requireCourse(courseId);
		return views(courseId);
	}

	@Transactional(readOnly = true)
	public List<SlotView> views(UUID courseId) {
		return slots.findByCourseIdAndDeletedAtIsNullOrderByPositionAsc(courseId).stream().map(CourseRoutineService::view)
			.toList();
	}

	private static boolean acceptsRoutine(String deliveryMode) {
		return "OFFLINE".equals(deliveryMode) || "HYBRID".equals(deliveryMode);
	}

	private static ParsedSlot parse(JsonNode node) {
		if (node == null || !node.isObject() || !node.has("weekday") || !node.has("startTime") || !node.has("endTime")) {
			throw validationFailed();
		}
		int weekday = node.get("weekday").asInt();
		if (weekday < 1 || weekday > 7) {
			throw validationFailed();
		}
		LocalTime start = time(node.get("startTime"));
		LocalTime end = time(node.get("endTime"));
		if (!end.isAfter(start)) {
			throw validationFailed();
		}
		String place = null;
		if (node.has("place") && !node.get("place").isNull()) {
			String text = node.get("place").asText().trim();
			place = text.isEmpty() ? null : text;
		}
		return new ParsedSlot((short) weekday, start, end, place);
	}

	private static LocalTime time(JsonNode node) {
		if (node == null || node.isNull()) {
			throw validationFailed();
		}
		try {
			return LocalTime.parse(node.asText().trim());
		}
		catch (DateTimeParseException exception) {
			throw validationFailed();
		}
	}

	private static void rejectOverlaps(List<ParsedSlot> parsed) {
		for (int left = 0; left < parsed.size(); left++) {
			for (int right = left + 1; right < parsed.size(); right++) {
				ParsedSlot a = parsed.get(left);
				ParsedSlot b = parsed.get(right);
				if (a.weekday() == b.weekday() && a.startTime().isBefore(b.endTime()) && b.startTime().isBefore(a.endTime())) {
					throw conflict(ErrorCode.ROUTINE_OVERLAP, "Routine slots on the same weekday overlap.");
				}
			}
		}
	}

	private Course requireCourse(UUID courseId) {
		return courses.findByIdAndDeletedAtIsNull(courseId).orElseThrow(CourseRoutineService::notFound);
	}

	private static SlotView view(CourseRoutineSlot slot) {
		return new SlotView(slot.getWeekday(), slot.getStartTime().toString(), slot.getEndTime().toString(), slot.getPlace(),
				slot.getPosition());
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private static ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	private static ApiException conflict(ErrorCode code, String message) {
		return new ApiException(code, HttpStatus.CONFLICT, message);
	}

	private record ParsedSlot(short weekday, LocalTime startTime, LocalTime endTime, String place) {
	}

	public record SlotView(int weekday, String startTime, String endTime, String place, int position) {
	}

}
