package com.coachingcenter.api.exams;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.common.config.AppProperties;
import com.coachingcenter.api.common.mail.EmailTemplates;
import com.coachingcenter.api.common.mail.MailContext;
import com.coachingcenter.api.common.mail.RenderedEmail;
import com.coachingcenter.api.users.StudentProfile;
import com.coachingcenter.api.users.StudentProfileRepository;
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

	private final StudentProfileRepository profiles;

	private final MailContext context;

	ExamMailTexts(LiveExamOrderRepository orders, LiveExamGrantRepository grants, LiveExamRepository exams,
			LiveExamModuleRepository modules, UserRepository users, LiveExamAccess access,
			StudentProfileRepository profiles, AppProperties properties) {
		this.orders = orders;
		this.grants = grants;
		this.exams = exams;
		this.modules = modules;
		this.users = users;
		this.access = access;
		this.profiles = profiles;
		this.context = MailContext.from(properties);
	}

	Prepared prepare(ExamOutboundMail row, Instant now) {
		if (ExamOutboundMail.PAYMENT_RECEIPT.equals(row.getKind())) {
			return receipt(row);
		}
		if (ExamOutboundMail.PURCHASE_NOTICE.equals(row.getKind())) {
			return purchaseNotice(row);
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
		Titles titles = titles(order);
		if (titles == null) {
			return null;
		}
		RenderedEmail mail = EmailTemplates.paymentReceipt(context, titles.bn(), titles.en(), order.getAmount().toPlainString(),
				order.getTranId(), TIME.format(order.getUpdatedAt()));
		return new Prepared(student.getEmail(), mail.subject(), mail.text(), mail.html());
	}

	private Prepared purchaseNotice(ExamOutboundMail row) {
		UUID id = orderId(row);
		if (id == null) {
			return null;
		}
		LiveExamOrder order = orders.findById(id).orElse(null);
		if (order == null || !LiveExamOrder.SUCCEEDED.equals(order.getStatus()) || !grants.existsByOrderId(order.getId())) {
			return null;
		}
		User admin = users.findByIdAndDeletedAtIsNull(row.getUserId()).orElse(null);
		if (admin == null || !Role.MASTER_ADMIN.name().equals(admin.getRole()) || !"ACTIVE".equals(admin.getStatus())
				|| admin.getEmail() == null || admin.getEmail().isBlank()) {
			return null;
		}
		User student = users.findById(order.getStudentId()).orElse(null);
		Titles titles = titles(order);
		if (student == null || titles == null) {
			return null;
		}
		String name = profiles.findById(student.getId()).map(StudentProfile::getFullName).orElse("-");
		String email = student.getEmail() == null ? "-" : student.getEmail();
		String channel = order.getPaymentChannel() == null ? "-" : order.getPaymentChannel();
		RenderedEmail mail = EmailTemplates.purchaseNotice(context, name, student.getPhone(), email, titles.bn(), titles.en(),
				order.getAmount().toPlainString(), channel, order.getTranId(), TIME.format(order.getUpdatedAt()));
		return new Prepared(admin.getEmail(), mail.subject(), mail.text(), mail.html());
	}

	private Titles titles(LiveExamOrder order) {
		if (LiveExamOrder.EXAM.equals(order.getScope())) {
			return exams.findByIdAndDeletedAtIsNull(order.getExamId())
				.map(exam -> new Titles(exam.getTitleBn(), exam.getTitleEn()))
				.orElse(null);
		}
		return modules.findByIdAndDeletedAtIsNull(order.getModuleId())
			.map(module -> new Titles(module.getTitleBn(), module.getTitleEn()))
			.orElse(null);
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
		RenderedEmail mail = EmailTemplates.examNotice(context, hour, exam.getTitleBn(), exam.getTitleEn(),
				TIME.format(exam.getLiveAt()), "/exam/" + exam.getId());
		return new Prepared(student.getEmail(), mail.subject(), mail.text(), mail.html());
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

	record Prepared(String to, String subject, String text, String html) {
	}

	private record Titles(String bn, String en) {
	}

	private record ParsedKey(UUID studentId, UUID examId, Instant liveAt) {
	}

}
