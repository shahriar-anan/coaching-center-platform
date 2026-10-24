package com.coachingcenter.api.exams;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.users.User;
import com.coachingcenter.api.users.UserRepository;

@Component
class ExamMailTexts {

	static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(DHAKA);

	private static final Duration HOUR_OPEN = Duration.ofMinutes(70);

	private static final Duration HOUR_CLOSE = Duration.ofMinutes(15);

	private final LiveExamOrderRepository orders;

	private final LiveExamGrantRepository grants;

	private final LiveExamRepository exams;

	private final LiveExamModuleRepository modules;

	private final UserRepository users;

	private final LiveExamAccess access;

	ExamMailTexts(LiveExamOrderRepository orders, LiveExamGrantRepository grants, LiveExamRepository exams,
			LiveExamModuleRepository modules, UserRepository users, LiveExamAccess access) {
		this.orders = orders;
		this.grants = grants;
		this.exams = exams;
		this.modules = modules;
		this.users = users;
		this.access = access;
	}

	Prepared prepare(ExamOutboundMail row, Instant now) {
		if (ExamOutboundMail.PAYMENT_RECEIPT.equals(row.getKind())) {
			return receipt(row);
		}
		if (ExamOutboundMail.EXAM_TIME.equals(row.getKind()) || ExamOutboundMail.HOUR_BEFORE.equals(row.getKind())) {
			return notice(row, now);
		}
		return null;
	}

	static boolean inHourWindow(Instant liveAt, Instant now) {
		if (liveAt == null || now == null) {
			return false;
		}
		return now.isAfter(liveAt.minus(HOUR_OPEN)) && now.isBefore(liveAt.minus(HOUR_CLOSE));
	}

	static String examKey(UUID studentId, LiveExam exam) {
		return studentId + "/" + exam.getId() + "/" + exam.getLiveAt();
	}

	private Prepared receipt(ExamOutboundMail row) {
		UUID id = orderId(row);
		if (id == null) {
			return null;
		}
		LiveExamOrder order = orders.findById(id).orElse(null);
		if (order == null || !LiveExamOrder.SUCCEEDED.equals(order.getStatus()) || !grants.existsByOrderId(order.getId())) {
			return null;
		}
		User student = activeStudent(row.getUserId());
		if (student == null || !student.getId().equals(order.getStudentId())) {
			return null;
		}
		String titleBn;
		String titleEn;
		if (LiveExamOrder.EXAM.equals(order.getScope())) {
			LiveExam exam = exams.findByIdAndDeletedAtIsNull(order.getExamId()).orElse(null);
			if (exam == null) {
				return null;
			}
			titleBn = exam.getTitleBn();
			titleEn = exam.getTitleEn();
		}
		else {
			LiveExamModule module = modules.findByIdAndDeletedAtIsNull(order.getModuleId()).orElse(null);
			if (module == null) {
				return null;
			}
			titleBn = module.getTitleBn();
			titleEn = module.getTitleEn();
		}
		String amount = order.getAmount().toPlainString();
		String text = "পেমেন্ট রসিদ\n" + titleBn + "\nপরিমাণ: " + amount + " BDT\nলেনদেন: " + order.getTranId()
				+ "\n\nPayment receipt\n" + titleEn + "\nAmount: " + amount + " BDT\nTransaction: " + order.getTranId()
				+ "\n";
		return new Prepared(student.getEmail(), "পেমেন্ট রসিদ / Payment receipt", text);
	}

	private Prepared notice(ExamOutboundMail row, Instant now) {
		ParsedKey key = parse(row.getMessageKey());
		if (key == null || !key.studentId().equals(row.getUserId())) {
			return null;
		}
		User student = activeStudent(row.getUserId());
		LiveExam exam = exams.findByIdAndDeletedAtIsNull(key.examId()).orElse(null);
		if (student == null || exam == null || exam.getLiveAt() == null || !exam.getLiveAt().equals(key.liveAt())) {
			return null;
		}
		if (!LiveExam.PUBLISHED.equals(exam.getStatus()) || !modulePublished(exam.getModuleId())) {
			return null;
		}
		if (!access.hasExam(student.getId(), exam)) {
			return null;
		}
		boolean hour = ExamOutboundMail.HOUR_BEFORE.equals(row.getKind());
		if (hour && !inHourWindow(exam.getLiveAt(), now)) {
			return null;
		}
		String time = TIME.format(exam.getLiveAt());
		String bnLead = hour ? "আপনার পরীক্ষা প্রায় এক ঘণ্টার মধ্যে শুরু হবে" : "পরীক্ষার সময় জানানো হয়েছে";
		String enLead = hour ? "Your exam starts in about one hour" : "An exam time has been announced";
		String text = bnLead + "\n" + exam.getTitleBn() + "\nশুরু: " + time + "\n\n" + enLead + "\n" + exam.getTitleEn()
				+ "\nStarts: " + time + "\n";
		String subject = hour ? "পরীক্ষা শুরুর আগে / Exam starting soon" : "পরীক্ষার সময় / Exam time";
		return new Prepared(student.getEmail(), subject, text);
	}

	private boolean modulePublished(UUID moduleId) {
		LiveExamModule module = modules.findByIdAndDeletedAtIsNull(moduleId).orElse(null);
		return module != null && LiveExamModule.PUBLISHED.equals(module.getStatus());
	}

	private User activeStudent(UUID userId) {
		User user = users.findByIdAndDeletedAtIsNull(userId).orElse(null);
		if (user == null || !Role.STUDENT.name().equals(user.getRole()) || !"ACTIVE".equals(user.getStatus())) {
			return null;
		}
		if (user.getEmail() == null || user.getEmail().isBlank()) {
			return null;
		}
		return user;
	}

	private static UUID orderId(ExamOutboundMail row) {
		try {
			return UUID.fromString(row.getMessageKey());
		}
		catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private static ParsedKey parse(String messageKey) {
		if (messageKey == null) {
			return null;
		}
		String[] parts = messageKey.split("/", 3);
		if (parts.length != 3) {
			return null;
		}
		try {
			return new ParsedKey(UUID.fromString(parts[0]), UUID.fromString(parts[1]), Instant.parse(parts[2]));
		}
		catch (RuntimeException exception) {
			return null;
		}
	}

	record Prepared(String to, String subject, String text) {
	}

	private record ParsedKey(UUID studentId, UUID examId, Instant liveAt) {
	}

}
