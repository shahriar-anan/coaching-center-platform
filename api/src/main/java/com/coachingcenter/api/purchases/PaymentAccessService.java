package com.coachingcenter.api.purchases;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.courses.Course;
import com.coachingcenter.api.courses.CourseRepository;
import com.coachingcenter.api.exams.ExamModule;
import com.coachingcenter.api.exams.ExamModulePurchase;
import com.coachingcenter.api.exams.ExamModulePurchaseRepository;
import com.coachingcenter.api.exams.ExamModulePurchaseService;
import com.coachingcenter.api.exams.ExamModuleRepository;
import com.coachingcenter.api.library.Book;
import com.coachingcenter.api.library.BookRepository;
import com.coachingcenter.api.users.StudentProfile;
import com.coachingcenter.api.users.StudentProfileRepository;
import com.coachingcenter.api.users.User;
import com.coachingcenter.api.users.UserRepository;

import tools.jackson.databind.JsonNode;

@Service
public class PaymentAccessService {

	private final PaymentRepository payments;

	private final EnrollmentRepository enrollments;

	private final BookOrderRepository orders;

	private final CourseRepository courses;

	private final BookRepository books;

	private final UserRepository users;

	private final StudentProfileRepository profiles;

	private final AuditRecorder audit;

	private final ExamModulePurchaseRepository examPurchases;

	private final ExamModuleRepository examModules;

	public PaymentAccessService(PaymentRepository payments, EnrollmentRepository enrollments,
			BookOrderRepository orders, CourseRepository courses, BookRepository books, UserRepository users,
			StudentProfileRepository profiles, AuditRecorder audit, ExamModulePurchaseRepository examPurchases,
			ExamModuleRepository examModules) {
		this.payments = payments;
		this.enrollments = enrollments;
		this.orders = orders;
		this.courses = courses;
		this.books = books;
		this.users = users;
		this.profiles = profiles;
		this.audit = audit;
		this.examPurchases = examPurchases;
		this.examModules = examModules;
	}

	@Transactional(readOnly = true)
	public Page<PaymentView> list(String status, String payableType, Pageable pageable) {
		Page<Payment> page = payableType == null
				? payments.findByStatusAndDeletedAtIsNullOrderByCreatedAtDesc(status, pageable)
				: payments.findByStatusAndPayableTypeAndDeletedAtIsNullOrderByCreatedAtDesc(status, payableType,
						pageable);
		return page.map(this::toView);
	}

	private PaymentView toView(Payment payment) {
		User student = users.findByIdAndDeletedAtIsNull(payment.getStudentId()).orElse(null);
		StudentProfile profile = profiles.findById(payment.getStudentId()).orElse(null);
		String titleEn = null;
		String titleBn = null;
		if ("COURSE_ENROLLMENT".equals(payment.getPayableType())) {
			Enrollment enrollment = enrollments.findByIdAndDeletedAtIsNull(payment.getPayableId()).orElse(null);
			if (enrollment != null) {
				Course course = courses.findByIdAndDeletedAtIsNull(enrollment.getCourseId()).orElse(null);
				if (course != null) {
					titleEn = course.getTitleEn();
					titleBn = course.getTitleBn();
				}
			}
		}
		else if ("BOOK_ORDER".equals(payment.getPayableType())) {
			BookOrder order = orders.findByIdAndDeletedAtIsNull(payment.getPayableId()).orElse(null);
			if (order != null) {
				Book book = books.findByIdAndDeletedAtIsNull(order.getBookId()).orElse(null);
				if (book != null) {
					titleEn = book.getTitleEn();
					titleBn = book.getTitleBn();
				}
			}
		}
		else if ("EXAM_MODULE".equals(payment.getPayableType())) {
			ExamModulePurchase purchase = examPurchases.findByIdAndDeletedAtIsNull(payment.getPayableId()).orElse(null);
			if (purchase != null) {
				ExamModule module = examModules.findByIdAndDeletedAtIsNull(purchase.getModuleId()).orElse(null);
				if (module != null) {
					titleEn = module.getTitleEn();
					titleBn = module.getTitleBn();
				}
			}
		}
		return new PaymentView(payment.getId(), payment.getStudentId(), payment.getPayableType(), payment.getPayableId(),
				payment.getAmount(), payment.getMethod(), payment.getTransactionRef(), payment.getStatus(),
				profile == null ? null : profile.getFullName(), student == null ? null : student.getPhone(), titleEn,
				titleBn, payment.getCreatedAt());
	}

	@Transactional(readOnly = true)
	public Page<AdminBookOrderView> listBookOrders(Pageable pageable) {
		return orders.findByDeletedAtIsNullOrderByCreatedAtDesc(pageable).map(AdminBookOrderView::from);
	}

	@Transactional
	public VerifyPaymentResult verifyPayment(UUID paymentId, JsonNode body) {
		String result = requiredResult(body);
		Payment payment = payments.findByIdAndDeletedAtIsNull(paymentId)
			.orElseThrow(() -> notFound("Payment not found."));
		if (!"PENDING".equals(payment.getStatus())) {
			throw conflict(ErrorCode.PAYMENT_NOT_PENDING, "Payment is not pending.");
		}
		if ("SUCCESS".equals(result)) {
			return verifySuccess(payment);
		}
		return verifyFailure(payment);
	}

	@Transactional
	public AdmissionResult admitStudent(UUID courseId, JsonNode body) {
		Course course = requireAdmissibleCourse(courseId);
		UUID studentId = requiredUuid(body, "studentId");
		requireStudent(studentId);
		if (enrollments.existsByStudentIdAndCourseIdAndOpeningNumberAndStatusAndDeletedAtIsNull(studentId, courseId,
				course.getOpeningNumber(), "ACTIVE")) {
			throw conflict(ErrorCode.ALREADY_ENROLLED, "Student already has access for this opening.");
		}
		String deliveryMode = resolveAdmissionDeliveryMode(course, body);
		Enrollment enrollment = new Enrollment();
		enrollment.setStudentId(studentId);
		enrollment.setCourseId(courseId);
		enrollment.setOpeningNumber(course.getOpeningNumber());
		enrollment.setDeliveryMode(deliveryMode);
		enrollment.setSource("OFFLINE");
		enrollment.setStatus("ACTIVE");
		enrollment.setPricePaid(course.getPrice());
		enrollment.setStartedAt(Instant.now());
		enrollment = enrollments.save(enrollment);
		return new AdmissionResult(enrollment.getId(), enrollment.getStatus(), enrollment.getOpeningNumber(),
				enrollment.getSource());
	}

	@Transactional
	public BookOrderStatusResult updateOrderStatus(UUID orderId, JsonNode body, Role actorRole) {
		String targetStatus = requiredText(body, "status");
		BookOrder order = orders.findByIdAndDeletedAtIsNull(orderId)
			.orElseThrow(() -> notFound("Order not found."));
		if ("COLLECTED".equals(targetStatus) && "CASH".equals(order.getPaymentMethod())) {
			if (actorRole != Role.MASTER_ADMIN) {
				throw denied();
			}
			if (!"READY".equals(order.getStatus())) {
				throw validationFailed();
			}
			order.setStatus("COLLECTED");
			orders.save(order);
			audit.record(AuditRecorder.CASH_RECEIVED, AuditRecorder.BOOK_ORDER_ENTITY, order.getId());
			return BookOrderStatusResult.from(order);
		}
		if ("READY".equals(targetStatus)) {
			assertCanFulfill(actorRole);
			advanceToReady(order);
			orders.save(order);
			return BookOrderStatusResult.from(order);
		}
		if ("DELIVERED".equals(targetStatus) || "COLLECTED".equals(targetStatus)) {
			assertCanFulfill(actorRole);
			advanceToTerminal(order, targetStatus);
			orders.save(order);
			return BookOrderStatusResult.from(order);
		}
		throw validationFailed();
	}

	private VerifyPaymentResult verifySuccess(Payment payment) {
		if ("COURSE_ENROLLMENT".equals(payment.getPayableType())) {
			Enrollment enrollment = enrollments.findByIdAndDeletedAtIsNull(payment.getPayableId())
				.orElseThrow(() -> notFound("Enrollment not found."));
			Course course = courses.findByIdAndDeletedAtIsNull(enrollment.getCourseId())
				.orElseThrow(() -> notFound("Course not found."));
			if (!courseCanActivate(course, enrollment)) {
				throw conflict(ErrorCode.COURSE_NOT_OPEN, "Course is not open for activation.");
			}
			payment.setStatus("SUCCESS");
			payments.save(payment);
			enrollment.setStatus("ACTIVE");
			enrollments.save(enrollment);
			audit.record(AuditRecorder.PAYMENT_SUCCESS, AuditRecorder.PAYMENT_ENTITY, payment.getId());
			return VerifyPaymentResult.from(payment, enrollment.getStatus(), null);
		}
		if ("BOOK_ORDER".equals(payment.getPayableType())) {
			BookOrder order = orders.findByIdAndDeletedAtIsNull(payment.getPayableId())
				.orElseThrow(() -> notFound("Order not found."));
			payment.setStatus("SUCCESS");
			payments.save(payment);
			order.setStatus("PAID");
			orders.save(order);
			audit.record(AuditRecorder.PAYMENT_SUCCESS, AuditRecorder.PAYMENT_ENTITY, payment.getId());
			return VerifyPaymentResult.from(payment, null, order.getStatus());
		}
		if ("EXAM_MODULE".equals(payment.getPayableType())) {
			ExamModulePurchase purchase = examPurchases.findByIdAndDeletedAtIsNull(payment.getPayableId())
				.orElseThrow(() -> notFound("Exam purchase not found."));
			ExamModule module = examModules.findByIdAndDeletedAtIsNull(purchase.getModuleId())
				.orElseThrow(() -> notFound("Exam module not found."));
			if (!ExamModulePurchaseService.canActivate(module)) {
				throw conflict(ErrorCode.EXAM_NOT_FOR_SALE, "This exam module is not for sale.");
			}
			payment.setStatus("SUCCESS");
			payments.save(payment);
			purchase.setStatus("ACTIVE");
			purchase.setStartedAt(Instant.now());
			examPurchases.save(purchase);
			audit.record(AuditRecorder.PAYMENT_SUCCESS, AuditRecorder.PAYMENT_ENTITY, payment.getId());
			return VerifyPaymentResult.from(payment, purchase.getStatus(), null);
		}
		throw validationFailed();
	}

	private VerifyPaymentResult verifyFailure(Payment payment) {
		payment.setStatus("FAILED");
		payments.save(payment);
		audit.record(AuditRecorder.PAYMENT_FAILED, AuditRecorder.PAYMENT_ENTITY, payment.getId());
		if ("COURSE_ENROLLMENT".equals(payment.getPayableType())) {
			Enrollment enrollment = enrollments.findByIdAndDeletedAtIsNull(payment.getPayableId())
				.orElseThrow(() -> notFound("Enrollment not found."));
			enrollment.setStatus("PAYMENT_FAILED");
			enrollments.save(enrollment);
			return VerifyPaymentResult.from(payment, enrollment.getStatus(), null);
		}
		if ("BOOK_ORDER".equals(payment.getPayableType())) {
			BookOrder order = orders.findByIdAndDeletedAtIsNull(payment.getPayableId())
				.orElseThrow(() -> notFound("Order not found."));
			order.setStatus("PAYMENT_FAILED");
			orders.save(order);
			return VerifyPaymentResult.from(payment, null, order.getStatus());
		}
		if ("EXAM_MODULE".equals(payment.getPayableType())) {
			ExamModulePurchase purchase = examPurchases.findByIdAndDeletedAtIsNull(payment.getPayableId())
				.orElseThrow(() -> notFound("Exam purchase not found."));
			purchase.setStatus("PAYMENT_FAILED");
			examPurchases.save(purchase);
			return VerifyPaymentResult.from(payment, purchase.getStatus(), null);
		}
		throw validationFailed();
	}

	private static boolean courseCanActivate(Course course, Enrollment enrollment) {
		return "PUBLISHED".equals(course.getStatus()) && !course.isClosed()
				&& enrollment.getOpeningNumber() == course.getOpeningNumber();
	}

	private Course requireAdmissibleCourse(UUID courseId) {
		Course course = courses.findByIdAndDeletedAtIsNull(courseId)
			.orElseThrow(() -> notFound("Course not found."));
		if (!"PUBLISHED".equals(course.getStatus()) || course.isClosed()) {
			throw conflict(ErrorCode.COURSE_NOT_OPEN, "Course is not open for admission.");
		}
		if ("FULL_FREE".equals(course.getAccessType())) {
			throw conflict(ErrorCode.COURSE_IS_FREE, "This course does not require admission.");
		}
		if (course.getPrice() == null) {
			throw conflict(ErrorCode.COURSE_NOT_OPEN, "Course is not open for admission.");
		}
		return course;
	}

	private void requireStudent(UUID studentId) {
		User user = users.findById(studentId).orElseThrow(() -> notFound("Student not found."));
		if (!Role.STUDENT.name().equals(user.getRole())) {
			throw notFound("Student not found.");
		}
	}

	private static String resolveAdmissionDeliveryMode(Course course, JsonNode body) {
		String courseMode = course.getDeliveryMode();
		if ("HYBRID".equals(courseMode)) {
			String requested = requiredText(body, "deliveryMode");
			if (!"ONLINE".equals(requested) && !"OFFLINE".equals(requested)) {
				throw validationFailed();
			}
			return requested;
		}
		return courseMode;
	}

	private static void advanceToReady(BookOrder order) {
		if ("ONLINE".equals(order.getPaymentMethod())) {
			if (!"PAID".equals(order.getStatus())) {
				throw validationFailed();
			}
		}
		else if (!"PLACED".equals(order.getStatus())) {
			throw validationFailed();
		}
		order.setStatus("READY");
	}

	private static void advanceToTerminal(BookOrder order, String targetStatus) {
		if (!"READY".equals(order.getStatus())) {
			throw validationFailed();
		}
		if ("DELIVERY".equals(order.getFulfillment())) {
			if (!"DELIVERED".equals(targetStatus)) {
				throw conflict(ErrorCode.FULFILLMENT_MISMATCH, "Delivery orders must be marked delivered.");
			}
		}
		else if (!"COLLECTED".equals(targetStatus)) {
			throw conflict(ErrorCode.FULFILLMENT_MISMATCH, "Pickup orders must be marked collected.");
		}
		order.setStatus(targetStatus);
	}

	private static void assertCanFulfill(Role actorRole) {
		if (actorRole != Role.MASTER_ADMIN && actorRole != Role.SYSTEM_ADMIN) {
			throw denied();
		}
	}

	private static String requiredResult(JsonNode body) {
		String result = requiredText(body, "result");
		if (!"SUCCESS".equals(result) && !"FAILED".equals(result)) {
			throw validationFailed();
		}
		return result;
	}

	private static UUID requiredUuid(JsonNode body, String field) {
		String value = requiredText(body, field);
		try {
			return UUID.fromString(value);
		}
		catch (IllegalArgumentException exception) {
			throw validationFailed();
		}
	}

	private static String requiredText(JsonNode body, String field) {
		JsonNode node = body.get(field);
		if (node == null || node.isNull() || node.asText().isBlank()) {
			throw validationFailed();
		}
		return node.asText().trim();
	}

	private static ApiException notFound(String message) {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, message);
	}

	private static ApiException conflict(ErrorCode code, String message) {
		return new ApiException(code, HttpStatus.CONFLICT, message);
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private static ApiException denied() {
		return new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
	}

	public record PaymentView(UUID id, UUID studentId, String payableType, UUID payableId, java.math.BigDecimal amount,
			String method, String transactionRef, String status, String studentName, String studentPhone, String titleEn,
			String titleBn, Instant createdAt) {
	}

	public record VerifyPaymentResult(UUID paymentId, String paymentStatus, String enrollmentStatus,
			String orderStatus) {

		static VerifyPaymentResult from(Payment payment, String enrollmentStatus, String orderStatus) {
			return new VerifyPaymentResult(payment.getId(), payment.getStatus(), enrollmentStatus, orderStatus);
		}

	}

	public record AdmissionResult(UUID enrollmentId, String status, int openingNumber, String source) {
	}

	public record BookOrderStatusResult(UUID orderId, String status, String fulfillment, String paymentMethod) {

		static BookOrderStatusResult from(BookOrder order) {
			return new BookOrderStatusResult(order.getId(), order.getStatus(), order.getFulfillment(),
					order.getPaymentMethod());
		}

	}

	public record AdminBookOrderView(UUID orderId, UUID studentId, UUID bookId, String status, String fulfillment,
			String paymentMethod, String phone, String address, String area) {

		static AdminBookOrderView from(BookOrder order) {
			return new AdminBookOrderView(order.getId(), order.getStudentId(), order.getBookId(), order.getStatus(),
					order.getFulfillment(), order.getPaymentMethod(), order.getPhone(), order.getAddress(),
					order.getArea());
		}

	}

}
