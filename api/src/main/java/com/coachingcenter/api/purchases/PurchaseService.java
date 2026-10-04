package com.coachingcenter.api.purchases;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.courses.Course;
import com.coachingcenter.api.courses.CourseRepository;
import com.coachingcenter.api.library.Book;
import com.coachingcenter.api.library.BookRepository;

import tools.jackson.databind.JsonNode;

@Service
public class PurchaseService {

	private final CourseRepository courses;

	private final BookRepository books;

	private final EnrollmentRepository enrollments;

	private final BookOrderRepository orders;

	private final PaymentRepository payments;

	private final PaymentProperties paymentProperties;

	public PurchaseService(CourseRepository courses, BookRepository books, EnrollmentRepository enrollments,
			BookOrderRepository orders, PaymentRepository payments, PaymentProperties paymentProperties) {
		this.courses = courses;
		this.books = books;
		this.enrollments = enrollments;
		this.orders = orders;
		this.payments = payments;
		this.paymentProperties = paymentProperties;
	}

	@Transactional
	public CoursePurchaseResult purchaseCourse(UUID courseId, UUID studentId, JsonNode body) {
		Course course = requireOpenCourse(courseId);
		if ("FULL_FREE".equals(course.getAccessType())) {
			throw conflict(ErrorCode.COURSE_IS_FREE, "This course does not require a purchase.");
		}
		if (course.getPrice() == null) {
			throw conflict(ErrorCode.COURSE_NOT_OPEN, "Course is not open for purchase.");
		}
		if (enrollments.existsByStudentIdAndCourseIdAndOpeningNumberAndStatusAndDeletedAtIsNull(studentId, courseId,
				course.getOpeningNumber(), "ACTIVE")) {
			throw conflict(ErrorCode.ALREADY_ENROLLED, "You already have access to this course for the current opening.");
		}
		String method = requiredText(body, "method");
		String transactionRef = requiredText(body, "transactionRef");
		validatePaymentMethod(method);
		ensureUniqueTransactionRef(transactionRef);
		String deliveryMode = resolveEnrollmentDeliveryMode(course, body);
		Enrollment enrollment = new Enrollment();
		enrollment.setStudentId(studentId);
		enrollment.setCourseId(courseId);
		enrollment.setOpeningNumber(course.getOpeningNumber());
		enrollment.setDeliveryMode(deliveryMode);
		enrollment.setSource("ONLINE");
		enrollment.setStatus("AWAITING_PAYMENT");
		enrollment.setPricePaid(course.getPrice());
		enrollment.setStartedAt(Instant.now());
		enrollment = enrollments.save(enrollment);
		Payment payment = createPayment(studentId, "COURSE_ENROLLMENT", enrollment.getId(), course.getPrice(), method,
				transactionRef);
		return new CoursePurchaseResult(enrollment.getId(), payment.getId(), enrollment.getStatus(), payment.getStatus(),
				payment.getAmount(), payment.getMethod(), payment.getTransactionRef(), paymentNumber(method));
	}

	@Transactional
	public BookOrderResult placeBookOrder(UUID bookId, UUID studentId, JsonNode body) {
		Book book = books.findByIdAndDeletedAtIsNull(bookId)
			.filter(Book::isAvailable)
			.orElseThrow(() -> conflict(ErrorCode.BOOK_UNAVAILABLE, "Book is not available."));
		if (book.getPrice() == null) {
			throw conflict(ErrorCode.BOOK_UNAVAILABLE, "Book is not available.");
		}
		String phone = requiredText(body, "phone");
		String fulfillment = requiredText(body, "fulfillment");
		String paymentMethod = requiredText(body, "paymentMethod");
		validateFulfillment(fulfillment);
		validateOrderPaymentMethod(paymentMethod);
		String address = text(body, "address");
		String area = text(body, "area");
		if ("DELIVERY".equals(fulfillment) && (address == null || address.isBlank())) {
			throw new ApiException(ErrorCode.ADDRESS_REQUIRED, HttpStatus.BAD_REQUEST, "Delivery address is required.");
		}
		if ("CASH".equals(paymentMethod) && "DELIVERY".equals(fulfillment)) {
			throw new ApiException(ErrorCode.CASH_REQUIRES_PICKUP, HttpStatus.BAD_REQUEST,
					"Cash payment requires pickup.");
		}
		BookOrder order = new BookOrder();
		order.setStudentId(studentId);
		order.setBookId(bookId);
		order.setFulfillment(fulfillment);
		order.setPaymentMethod(paymentMethod);
		order.setStatus("PLACED");
		order.setPhone(phone);
		order.setAddress(address);
		order.setArea(area);
		order = orders.save(order);
		Payment payment = null;
		if ("ONLINE".equals(paymentMethod)) {
			String method = requiredText(body, "method");
			String transactionRef = requiredText(body, "transactionRef");
			validatePaymentMethod(method);
			ensureUniqueTransactionRef(transactionRef);
			payment = createPayment(studentId, "BOOK_ORDER", order.getId(), book.getPrice(), method, transactionRef);
		}
		return new BookOrderResult(order.getId(), order.getStatus(), order.getFulfillment(), order.getPaymentMethod(),
				payment == null ? null : payment.getId(), payment == null ? null : payment.getStatus(),
				payment == null ? null : paymentNumber(payment.getMethod()));
	}

	@Transactional(readOnly = true)
	public List<CoursePurchaseView> listMyPurchases(UUID studentId) {
		return payments.findByStudentIdAndDeletedAtIsNullOrderByCreatedAtDesc(studentId)
			.stream()
			.filter(payment -> "COURSE_ENROLLMENT".equals(payment.getPayableType()))
			.map(payment -> {
				Enrollment enrollment = enrollments.findById(payment.getPayableId()).orElseThrow();
				return new CoursePurchaseView(enrollment.getId(), enrollment.getCourseId(), enrollment.getStatus(),
						payment.getStatus(), payment.getAmount(), payment.getMethod(), payment.getTransactionRef());
			})
			.toList();
	}

	@Transactional(readOnly = true)
	public List<BookOrderView> listMyOrders(UUID studentId) {
		return orders.findByStudentIdAndDeletedAtIsNullOrderByCreatedAtDesc(studentId)
			.stream()
			.map(order -> new BookOrderView(order.getId(), order.getBookId(), order.getStatus(), order.getFulfillment(),
					order.getPaymentMethod(), order.getPhone(), order.getAddress(), order.getArea()))
			.toList();
	}

	private Payment createPayment(UUID studentId, String payableType, UUID payableId, java.math.BigDecimal amount,
			String method, String transactionRef) {
		Payment payment = new Payment();
		payment.setStudentId(studentId);
		payment.setPayableType(payableType);
		payment.setPayableId(payableId);
		payment.setAmount(amount);
		payment.setMethod(method);
		payment.setTransactionRef(transactionRef);
		payment.setStatus("PENDING");
		try {
			return payments.save(payment);
		}
		catch (DataIntegrityViolationException exception) {
			throw conflict(ErrorCode.DUPLICATE_TRANSACTION_REF, "Transaction reference is already in use.");
		}
	}

	private void ensureUniqueTransactionRef(String transactionRef) {
		if (payments.existsByTransactionRef(transactionRef)) {
			throw conflict(ErrorCode.DUPLICATE_TRANSACTION_REF, "Transaction reference is already in use.");
		}
	}

	private Course requireOpenCourse(UUID courseId) {
		return courses.findCatalogCourse(courseId)
			.orElseThrow(() -> conflict(ErrorCode.COURSE_NOT_OPEN, "Course is not open for purchase."));
	}

	private static String resolveEnrollmentDeliveryMode(Course course, JsonNode body) {
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

	private String paymentNumber(String method) {
		if ("BKASH".equals(method)) {
			return paymentProperties.bkashNumber();
		}
		if ("NAGAD".equals(method)) {
			return paymentProperties.nagadNumber();
		}
		throw validationFailed();
	}

	private static void validatePaymentMethod(String method) {
		if (!"BKASH".equals(method) && !"NAGAD".equals(method)) {
			throw validationFailed();
		}
	}

	private static void validateFulfillment(String fulfillment) {
		if (!"PICKUP".equals(fulfillment) && !"DELIVERY".equals(fulfillment)) {
			throw validationFailed();
		}
	}

	private static void validateOrderPaymentMethod(String paymentMethod) {
		if (!"ONLINE".equals(paymentMethod) && !"CASH".equals(paymentMethod)) {
			throw validationFailed();
		}
	}

	private static String requiredText(JsonNode body, String field) {
		String value = text(body, field);
		if (value == null || value.isBlank()) {
			throw validationFailed();
		}
		return value.trim();
	}

	private static String text(JsonNode body, String field) {
		JsonNode node = body.get(field);
		if (node == null || node.isNull()) {
			return null;
		}
		return node.asText();
	}

	private static ApiException conflict(ErrorCode code, String message) {
		return new ApiException(code, HttpStatus.CONFLICT, message);
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	public record CoursePurchaseResult(UUID enrollmentId, UUID paymentId, String enrollmentStatus, String paymentStatus,
			java.math.BigDecimal amount, String method, String transactionRef, String paymentNumber) {
	}

	public record CoursePurchaseView(UUID enrollmentId, UUID courseId, String enrollmentStatus, String paymentStatus,
			java.math.BigDecimal amount, String method, String transactionRef) {
	}

	public record BookOrderResult(UUID orderId, String status, String fulfillment, String paymentMethod, UUID paymentId,
			String paymentStatus, String paymentNumber) {
	}

	public record BookOrderView(UUID orderId, UUID bookId, String status, String fulfillment, String paymentMethod,
			String phone, String address, String area) {
	}

}
