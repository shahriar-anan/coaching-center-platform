package com.coachingcenter.api.exams;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.common.config.AppProperties;
import com.coachingcenter.api.common.mail.EmailTemplates;
import com.coachingcenter.api.common.mail.MailContext;
import com.coachingcenter.api.common.mail.MailRecipient;
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

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

	private final InstallmentPlanReader installments;

	private final LiveExamOrderRepository orders;

	private final LiveExamGrantRepository grants;

	private final LiveExamRepository exams;

	private final LiveExamModuleRepository modules;

	private final LiveExamCatalogVisibility visibility;

	private final UserRepository users;

	private final LiveExamAccess access;

	private final StudentProfileRepository profiles;

	private final MailContext context;

	ExamMailTexts(LiveExamOrderRepository orders, LiveExamGrantRepository grants, LiveExamRepository exams,
			LiveExamModuleRepository modules, LiveExamCatalogVisibility visibility, UserRepository users,
			LiveExamAccess access, StudentProfileRepository profiles, AppProperties properties,
			InstallmentPlanReader installments) {
		this.installments = installments;
		this.orders = orders;
		this.grants = grants;
		this.exams = exams;
		this.modules = modules;
		this.visibility = visibility;
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
		if (ExamOutboundMail.INSTALLMENT_RECEIPT.equals(row.getKind())) {
			return installmentReceipt(row);
		}
		if (ExamOutboundMail.INSTALLMENT_DUE_SOON.equals(row.getKind()) || ExamOutboundMail.INSTALLMENT_OVERDUE.equals(row.getKind())) {
			return installmentReminder(row);
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

	/**
	 * Where a receipt goes (Phase 4B): the address typed for the order, else the account email when it is verified, else
	 * nowhere (null). The queue uses the same rule, so no receipt row is made for an order with nowhere to send it.
	 */
	static String receiptAddress(LiveExamOrder order, User student) {
		if (order.getReceiptEmail() != null && !order.getReceiptEmail().isBlank()) {
			return order.getReceiptEmail();
		}
		if (student.getEmailVerifiedAt() != null && student.getEmail() != null && !student.getEmail().isBlank()) {
			return student.getEmail();
		}
		return null;
	}

	/** Where a receipt with no typed address goes: the account email, and only when it is verified (plan section 7). */
	static String receiptAddress(User student) {
		if (student.getEmailVerifiedAt() != null && student.getEmail() != null && !student.getEmail().isBlank()) {
			return student.getEmail();
		}
		return null;
	}

	/**
	 * The receipt of one payment on a direct enrolment's plan (plan section 7), with the receipt number and a link, not an
	 * attachment. Nothing goes for a voided payment, since its receipt is no longer valid, nor to a student who is not active
	 * or has no verified email.
	 */
	private Prepared installmentReceipt(ExamOutboundMail row) {
		UUID paymentId = uuidOf(row.getMessageKey());
		if (paymentId == null) {
			return null;
		}
		InstallmentPlanReader.PaymentReceipt receipt = installments.receipt(paymentId, null).orElse(null);
		if (receipt == null || receipt.voided()) {
			return null;
		}
		User student = activeStudent(row.getUserId());
		if (student == null || !student.getId().equals(receipt.studentId())) {
			return null;
		}
		String to = receiptAddress(student);
		if (to == null) {
			return null;
		}
		RenderedEmail mail = EmailTemplates.installmentReceipt(context, new MailRecipient(to, receipt.studentName(), Role.STUDENT),
				!receipt.exam(), nullToEmpty(receipt.itemTitleBn()), nullToEmpty(receipt.itemTitleEn()),
				receipt.amount().toPlainString(), InstallmentPlanReader.methodLabel(receipt.method()), receipt.reference(),
				DAY.format(receipt.paidOn()), receipt.receiptNo(), receipt.dueAfter().toPlainString(),
				receipt.nextDueOn() == null ? null : DAY.format(receipt.nextDueOn()),
				receipt.nextDueAmount() == null ? null : receipt.nextDueAmount().toPlainString());
		return new Prepared(to, mail.subject(), mail.text(), mail.html());
	}

	/**
	 * A reminder that an installment is due soon or overdue (plan section 7), to the student at the verified address, or to the
	 * Master Admin. It is checked again now: skipped when the installment was rescheduled away, when the plan has no live payment,
	 * when its access is revoked, when nothing is owed on the row any more, or when there is no address to send to.
	 */
	private Prepared installmentReminder(ExamOutboundMail row) {
		boolean overdue = ExamOutboundMail.INSTALLMENT_OVERDUE.equals(row.getKind());
		String[] parts = row.getMessageKey() == null ? new String[0] : row.getMessageKey().split("/");
		if (parts.length != (overdue ? 4 : 3) || row.getUserId() == null || !row.getUserId().toString().equals(parts[0])) {
			return null;
		}
		UUID installmentId = uuidOf(parts[1]);
		LocalDate dueOn = dateOf(parts[2]);
		if (installmentId == null || dueOn == null) {
			return null;
		}
		InstallmentPlanReader.DueRow due = installments.dueRow(installmentId).orElse(null);
		if (due == null || !dueOn.equals(due.dueOn()) || due.revoked() || !due.livePayment() || due.uncovered().signum() <= 0) {
			return null;
		}
		boolean toStudent = due.studentId().equals(row.getUserId());
		MailRecipient to;
		String link;
		if (toStudent) {
			User student = activeStudent(row.getUserId());
			String address = student == null ? null : receiptAddress(student);
			if (address == null) {
				return null;
			}
			to = new MailRecipient(address, due.studentName(), Role.STUDENT);
			link = context.site("/my-modules?tab=offline");
		}
		else {
			User admin = users.findByIdAndDeletedAtIsNull(row.getUserId()).orElse(null);
			if (admin == null || !Role.MASTER_ADMIN.name().equals(admin.getRole()) || !"ACTIVE".equals(admin.getStatus())
					|| admin.getEmail() == null || admin.getEmail().isBlank()) {
				return null;
			}
			to = new MailRecipient(admin.getEmail(), null, Role.MASTER_ADMIN);
			link = context.forRole(Role.MASTER_ADMIN, "/staff/installments/" + due.planId());
		}
		RenderedEmail mail = EmailTemplates.installmentReminder(context, to, overdue, toStudent, nullToEmpty(due.studentName()),
				nullToEmpty(due.itemTitleBn()), nullToEmpty(due.itemTitleEn()), due.uncovered().toPlainString(), DAY.format(dueOn),
				due.due().toPlainString(), link);
		return new Prepared(to.email(), mail.subject(), mail.text(), mail.html());
	}

	/** The message key of an installment reminder: the recipient, the installment, its due date, and for overdue the week. */
	static String installmentKey(UUID recipientId, UUID installmentId, LocalDate dueOn, String week) {
		return recipientId + "/" + installmentId + "/" + dueOn + (week == null ? "" : "/" + week);
	}

	private static LocalDate dateOf(String value) {
		try {
			return LocalDate.parse(value);
		}
		catch (DateTimeException exception) {
			return null;
		}
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private static UUID uuidOf(String value) {
		try {
			return UUID.fromString(value);
		}
		catch (IllegalArgumentException | NullPointerException exception) {
			return null;
		}
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
		String to = receiptAddress(order, student);
		if (to == null) {
			return null;
		}
		Titles titles = titles(order);
		if (titles == null) {
			return null;
		}
		String buyerName = profiles.findById(student.getId()).map(StudentProfile::getFullName).orElse(null);
		boolean module = !LiveExamOrder.EXAM.equals(order.getScope());
		RenderedEmail mail = EmailTemplates.paymentReceipt(context, new MailRecipient(to, buyerName, Role.STUDENT), module,
				titles.bn(), titles.en(), order.getAmount().toPlainString(), order.getPaymentChannel(), order.getTranId(),
				TIME.format(order.getUpdatedAt()), module ? "/my-modules" : "/exam/" + order.getExamId(), order.getReceiptNo());
		return new Prepared(to, mail.subject(), mail.text(), mail.html());
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
		// NR-01 (contracts section 8): an exam is visible when it is published and its home category branch is open. A
		// standalone exam has no package, so the package cannot decide.
		if (!LiveExam.PUBLISHED.equals(exam.getStatus()) || visibility.branchHidden(exam.getCategoryId())) {
			return null;
		}
		if (!access.canSit(student.getId(), exam, now)) {
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
