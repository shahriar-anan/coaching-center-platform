package com.coachingcenter.api.exams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CapturingExamMailSender implements ExamMailSender {

	private final List<Sent> sent = Collections.synchronizedList(new ArrayList<>());

	private int failuresLeft;

	private int attemptCount;

	private boolean block;

	private CountDownLatch entered = new CountDownLatch(1);

	private CountDownLatch release = new CountDownLatch(1);

	@Override
	public void send(String to, String subject, String text) {
		attemptCount++;
		if (block) {
			block = false;
			entered.countDown();
			try {
				if (!release.await(5, TimeUnit.SECONDS)) {
					throw new IllegalStateException("exam mail test did not release the sender");
				}
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("exam mail test was interrupted");
			}
		}
		if (failuresLeft > 0) {
			failuresLeft--;
			throw new IllegalStateException("smtp down");
		}
		sent.add(new Sent(to, subject, text));
	}

	public void clear() {
		sent.clear();
		failuresLeft = 0;
		attemptCount = 0;
		block = false;
		entered = new CountDownLatch(1);
		release = new CountDownLatch(1);
	}

	public void failTimes(int times) {
		this.failuresLeft = times;
	}

	public void blockNext(CountDownLatch entered, CountDownLatch release) {
		this.entered = entered;
		this.release = release;
		this.block = true;
	}

	public List<Sent> sentTo(String email) {
		return sent.stream().filter(message -> message.to().equals(email)).toList();
	}

	public int attempts() {
		return attemptCount;
	}

	public int sentCount() {
		return sent.size();
	}

	public record Sent(String to, String subject, String text) {
	}

}
