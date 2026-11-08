package com.coachingcenter.api.staffnotifications;

import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * The tabs of the staff notification centre (plan section 7). {@link #INSTALLMENT} holds the direct-enrolment kinds. {@link #OTHER}
 * is every other kind, so the two counts add up to the unread total.
 */
public enum StaffNotificationGroup {

	INSTALLMENT,

	OTHER;

	private static final Set<String> INSTALLMENT_KINDS = Set.of(StaffNotificationKind.INSTALLMENT_PAYMENT_RECORDED.name(),
			StaffNotificationKind.INSTALLMENT_DUE_SOON.name(), StaffNotificationKind.INSTALLMENT_OVERDUE.name());

	/** The kinds of {@link #INSTALLMENT}. {@link #OTHER} is the complement of these. */
	public static Set<String> installmentKinds() {
		return INSTALLMENT_KINDS;
	}

	/** Whether a stored kind belongs to {@link #INSTALLMENT}. */
	public static boolean isInstallment(String kind) {
		return INSTALLMENT_KINDS.contains(kind);
	}

	/** Reads the {@code group} query value. Absent means every notification (null). An unknown value is a validation error. */
	public static StaffNotificationGroup parse(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return valueOf(value.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException exception) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
	}

}
