package com.coachingcenter.api.exams;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Conditional(SslCommerzConfigured.class)
class LiveExamPaymentRecheck {

	static final int MAX_ATTEMPTS = 5;

	private static final Logger log = LoggerFactory.getLogger(LiveExamPaymentRecheck.class);

	private final LiveExamOrderRepository orders;

	private final LiveExamOrderTx tx;

	private final LiveExamPaymentGateway gateway;

	private final Clock clock;

	private final ConcurrentHashMap<String, Integer> attempts = new ConcurrentHashMap<>();

	LiveExamPaymentRecheck(LiveExamOrderRepository orders, LiveExamOrderTx tx, LiveExamPaymentGateway gateway,
			Clock clock) {
		this.orders = orders;
		this.tx = tx;
		this.gateway = gateway;
		this.clock = clock;
	}

	@Scheduled(fixedDelay = 10, initialDelay = 10, timeUnit = TimeUnit.MINUTES)
	public void recheck() {
		sweep(clock.instant());
	}

	void sweep(Instant now) {
		Instant from = now.minus(Duration.ofHours(24));
		Instant to = now.minus(Duration.ofMinutes(10));
		for (LiveExamOrder order : orders.findByStatusAndCreatedAtBetween(LiveExamOrder.INITIATED, from, to)) {
			consider(order);
		}
	}

	private void consider(LiveExamOrder order) {
		String tranId = order.getTranId();
		if (attempts.getOrDefault(tranId, 0) >= MAX_ATTEMPTS) {
			return;
		}
		try {
			LiveExamPaymentGateway.Lookup lookup = gateway.lookup(tranId, order.getAmount(), order.getCurrency());
			switch (lookup.kind()) {
				case UNREACHABLE -> {
				}
				case NONE -> attempts.merge(tranId, 1, Integer::sum);
				case FAILED -> {
					tx.mark(tranId, LiveExamOrder.FAILED);
					attempts.remove(tranId);
				}
				case CANCELLED -> {
					tx.mark(tranId, LiveExamOrder.CANCELLED);
					attempts.remove(tranId);
				}
				case PAID -> confirm(order, lookup.valId());
			}
		}
		catch (RuntimeException exception) {
			log.warn("sslcommerz recheck failed tranId={} error={}", tranId, exception.getClass().getSimpleName());
		}
	}

	private void confirm(LiveExamOrder order, String valId) {
		LiveExamPaymentGateway.Verification verification = gateway.verify(order.getTranId(), valId, order.getAmount(),
				order.getCurrency());
		if (verification.outcome() == LiveExamPaymentGateway.Verification.Outcome.UNREACHABLE) {
			return;
		}
		tx.verify(order.getTranId(), verification);
		attempts.remove(order.getTranId());
	}

}
