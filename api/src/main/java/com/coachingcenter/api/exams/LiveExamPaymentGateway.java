package com.coachingcenter.api.exams;

import java.math.BigDecimal;

public interface LiveExamPaymentGateway {

	Started start(Checkout checkout);

	Verification verify(String tranId, String valId, BigDecimal amount, String currency);

	Lookup lookup(String tranId, BigDecimal amount, String currency);

	record Checkout(String tranId, BigDecimal amount, String currency, String productName, String customerName,
			String email, String phone) {
	}

	record Started(String redirectUrl) {
	}

	/**
	 * {@code paymentChannel} is the payer's channel as the gateway names it (for example {@code BKASH-BKash}), or null.
	 */
	record Verification(Outcome outcome, String tranId, BigDecimal amount, String currency, String valId,
			String paymentChannel) {

		public enum Outcome {

			VALID, INVALID, UNREACHABLE

		}

		public static Verification valid(String tranId, BigDecimal amount, String currency, String valId) {
			return valid(tranId, amount, currency, valId, null);
		}

		public static Verification valid(String tranId, BigDecimal amount, String currency, String valId,
				String paymentChannel) {
			return new Verification(Outcome.VALID, tranId, amount, currency, valId, paymentChannel);
		}

		public static Verification invalid() {
			return new Verification(Outcome.INVALID, null, null, null, null, null);
		}

		public static Verification unreachable() {
			return new Verification(Outcome.UNREACHABLE, null, null, null, null, null);
		}

	}

	record Lookup(Kind kind, String valId) {

		public enum Kind {

			PAID, FAILED, CANCELLED, NONE, UNREACHABLE

		}

		public static Lookup paid(String valId) {
			return new Lookup(Kind.PAID, valId);
		}

		public static Lookup of(Kind kind) {
			return new Lookup(kind, null);
		}

	}

}
