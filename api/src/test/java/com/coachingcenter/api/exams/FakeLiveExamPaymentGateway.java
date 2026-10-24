package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

public class FakeLiveExamPaymentGateway implements LiveExamPaymentGateway {

	public enum Mode {

		VALID, INVALID, UNREACHABLE, REJECT_SESSION

	}

	private Mode mode = Mode.VALID;

	private Lookup.Kind lookupKind = Lookup.Kind.NONE;

	private String lookupValId = "val-1";

	private RuntimeException lookupFailure;

	private int lookups;

	private final Map<String, BigDecimal> amounts = new ConcurrentHashMap<>();

	public void mode(Mode mode) {
		this.mode = mode;
	}

	public void lookup(Lookup.Kind kind) {
		this.lookupKind = kind;
		this.lookupFailure = null;
	}

	public void failLookup(RuntimeException exception) {
		this.lookupFailure = exception;
	}

	public int lookups() {
		return lookups;
	}

	public void reset() {
		mode = Mode.VALID;
		lookupKind = Lookup.Kind.NONE;
		lookupValId = "val-1";
		lookupFailure = null;
		lookups = 0;
		amounts.clear();
	}

	@Override
	public Started start(Checkout checkout) {
		if (mode == Mode.REJECT_SESSION) {
			throw new ApiException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
					"The payment gateway is unavailable.");
		}
		amounts.put(checkout.tranId(), checkout.amount());
		return new Started("https://gateway.test/" + checkout.tranId());
	}

	@Override
	public Verification verify(String tranId, String valId, BigDecimal amount, String currency) {
		if (mode == Mode.UNREACHABLE) {
			return Verification.unreachable();
		}
		if (mode == Mode.INVALID || valId == null || valId.isBlank()) {
			return Verification.invalid();
		}
		BigDecimal stored = amounts.get(tranId);
		if (stored == null || amount == null || stored.compareTo(amount) != 0 || currency == null
				|| !"BDT".equals(currency.trim())) {
			return Verification.invalid();
		}
		return Verification.valid(tranId, stored, "BDT", valId);
	}

	@Override
	public Lookup lookup(String tranId, BigDecimal amount, String currency) {
		lookups++;
		if (lookupFailure != null) {
			throw lookupFailure;
		}
		if (lookupKind == Lookup.Kind.PAID) {
			return Lookup.paid(lookupValId);
		}
		return Lookup.of(lookupKind);
	}

}
