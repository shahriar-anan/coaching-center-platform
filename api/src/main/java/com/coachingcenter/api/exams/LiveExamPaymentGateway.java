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

	record Verification(Outcome outcome, String tranId, BigDecimal amount, String currency, String valId) {

		public enum Outcome {

			VALID, INVALID, UNREACHABLE

		}

		public static Verification valid(String tranId, BigDecimal amount, String currency, String valId) {
			return new Verification(Outcome.VALID, tranId, amount, currency, valId);
		}

		public static Verification invalid() {
			return new Verification(Outcome.INVALID, null, null, null, null);
		}

		public static Verification unreachable() {
			return new Verification(Outcome.UNREACHABLE, null, null, null, null);
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
