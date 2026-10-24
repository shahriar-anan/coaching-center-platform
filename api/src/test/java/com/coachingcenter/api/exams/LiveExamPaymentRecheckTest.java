package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LiveExamPaymentRecheckTest {

	private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");

	@Mock
	private LiveExamOrderRepository orders;

	@Mock
	private LiveExamOrderTx tx;

	private ScriptedGateway gateway;

	private LiveExamPaymentRecheck recheck;

	private LiveExamOrder order;

	@BeforeEach
	void setUp() {
		gateway = new ScriptedGateway();
		recheck = new LiveExamPaymentRecheck(orders, tx, gateway, Clock.fixed(NOW, ZoneOffset.UTC));
		order = new LiveExamOrder();
		order.setTranId("tran-1");
		order.setAmount(new BigDecimal("500.00"));
		order.setCurrency("BDT");
		order.setStatus(LiveExamOrder.INITIATED);
		when(orders.findByStatusAndCreatedAtBetween(eq(LiveExamOrder.INITIATED), any(), any())).thenAnswer(
				invocation -> LiveExamOrder.INITIATED.equals(order.getStatus()) ? java.util.List.of(order) : java.util.List.of());
	}

	@Test
	void sweepUsesTheTenMinuteToTwentyFourHourWindow() {
		gateway.lookup = LiveExamPaymentGateway.Lookup.of(LiveExamPaymentGateway.Lookup.Kind.NONE);
		recheck.sweep(NOW);
		ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
		ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
		verify(orders).findByStatusAndCreatedAtBetween(eq(LiveExamOrder.INITIATED), from.capture(), to.capture());
		assertThat(from.getValue()).isEqualTo(NOW.minus(Duration.ofHours(24)));
		assertThat(to.getValue()).isEqualTo(NOW.minus(Duration.ofMinutes(10)));
	}

	@Test
	void fiveEmptyQueriesStopFurtherChecks() {
		gateway.lookup = LiveExamPaymentGateway.Lookup.of(LiveExamPaymentGateway.Lookup.Kind.NONE);
		for (int i = 0; i < 6; i++) {
			recheck.sweep(NOW);
		}
		assertThat(gateway.lookups).isEqualTo(5);
		gateway.lookup = LiveExamPaymentGateway.Lookup.paid("val-1");
		recheck.sweep(NOW);
		assertThat(gateway.lookups).isEqualTo(5);
		verify(tx, never()).verify(any(), any());
	}

	@Test
	void anUnreachableQueryDoesNotUseAnAttempt() {
		gateway.lookup = LiveExamPaymentGateway.Lookup.of(LiveExamPaymentGateway.Lookup.Kind.UNREACHABLE);
		for (int i = 0; i < 6; i++) {
			recheck.sweep(NOW);
		}
		assertThat(gateway.lookups).isEqualTo(6);
		verify(tx, never()).mark(any(), any());
	}

	@Test
	void aPaidQueryGrantsOnlyAfterValidation() {
		gateway.lookup = LiveExamPaymentGateway.Lookup.paid("val-1");
		gateway.verification = LiveExamPaymentGateway.Verification.valid("tran-1", order.getAmount(), "BDT", "val-1");
		when(tx.verify(eq("tran-1"), eq(gateway.verification))).thenAnswer(invocation -> {
			order.setStatus(LiveExamOrder.SUCCEEDED);
			return order;
		});
		recheck.sweep(NOW);
		verify(tx).verify("tran-1", gateway.verification);
		assertThat(gateway.verifies).isEqualTo(1);
		recheck.sweep(NOW);
		assertThat(gateway.lookups).isEqualTo(1);
	}

	@Test
	void aPaidQueryDoesNotGrantWhenValidationIsUnreachable() {
		gateway.lookup = LiveExamPaymentGateway.Lookup.paid("val-1");
		gateway.verification = LiveExamPaymentGateway.Verification.unreachable();
		recheck.sweep(NOW);
		recheck.sweep(NOW);
		assertThat(gateway.verifies).isEqualTo(2);
		verify(tx, never()).verify(any(), any());
	}

	@Test
	void aFailedQueryMarksTheOrderFailedAndACancelledQueryCancelsIt() {
		gateway.lookup = LiveExamPaymentGateway.Lookup.of(LiveExamPaymentGateway.Lookup.Kind.FAILED);
		when(tx.mark("tran-1", LiveExamOrder.FAILED)).thenAnswer(invocation -> {
			order.setStatus(LiveExamOrder.FAILED);
			return order;
		});
		recheck.sweep(NOW);
		verify(tx).mark("tran-1", LiveExamOrder.FAILED);

		order.setStatus(LiveExamOrder.INITIATED);
		gateway.lookup = LiveExamPaymentGateway.Lookup.of(LiveExamPaymentGateway.Lookup.Kind.CANCELLED);
		when(tx.mark("tran-1", LiveExamOrder.CANCELLED)).thenAnswer(invocation -> {
			order.setStatus(LiveExamOrder.CANCELLED);
			return order;
		});
		recheck.sweep(NOW);
		verify(tx).mark("tran-1", LiveExamOrder.CANCELLED);
	}

	private static final class ScriptedGateway implements LiveExamPaymentGateway {

		private LiveExamPaymentGateway.Lookup lookup = LiveExamPaymentGateway.Lookup.of(LiveExamPaymentGateway.Lookup.Kind.NONE);

		private LiveExamPaymentGateway.Verification verification = LiveExamPaymentGateway.Verification.invalid();

		private int lookups;

		private int verifies;

		@Override
		public Started start(Checkout checkout) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Verification verify(String tranId, String valId, BigDecimal amount, String currency) {
			verifies++;
			return verification;
		}

		@Override
		public Lookup lookup(String tranId, BigDecimal amount, String currency) {
			lookups++;
			return lookup;
		}

	}

}
