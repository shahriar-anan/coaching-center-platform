package com.coachingcenter.api.exams;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/** The one-hour-before exam mail is off by default: the scan never queues it. */
class ExamMailHourGateTest {

	@Test
	void theHourBeforeMailIsNotQueuedWhenTheGateIsOff() {
		ExamMailQueue queue = mock(ExamMailQueue.class);
		ExamMailDelivery delivery = mock(ExamMailDelivery.class);
		Clock clock = Clock.fixed(Instant.parse("2026-10-26T10:00:00Z"), ZoneOffset.UTC);
		ExamMailService service = new ExamMailService(queue, delivery, clock, false);

		service.scan();

		verify(queue, never()).enqueueHourBefore(Instant.parse("2026-10-26T10:00:00Z"));
	}
}
