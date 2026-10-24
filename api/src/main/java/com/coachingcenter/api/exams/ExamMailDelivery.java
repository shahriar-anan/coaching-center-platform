package com.coachingcenter.api.exams;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ExamMailDelivery {

	static final int MAX_ATTEMPTS = 3;

	private static final Logger log = LoggerFactory.getLogger(ExamMailDelivery.class);

	private final ExamOutboundMailRepository mail;

	private final ExamMailTexts texts;

	private final ExamMailSender sender;

	private final Clock clock;

	ExamMailDelivery(ExamOutboundMailRepository mail, ExamMailTexts texts, ExamMailSender sender, Clock clock) {
		this.mail = mail;
		this.texts = texts;
		this.sender = sender;
		this.clock = clock;
	}

	@Transactional
	public void deliverOne(UUID id) {
		ExamOutboundMail row = mail.lockById(id).orElse(null);
		if (row == null || !ExamOutboundMail.PENDING.equals(row.getStatus())) {
			return;
		}
		Instant now = clock.instant();
		ExamMailTexts.Prepared prepared = texts.prepare(row, now);
		if (prepared == null) {
			row.setStatus(ExamOutboundMail.SKIPPED);
			row.setUpdatedAt(now);
			log.info("exam mail skipped id={} kind={}", row.getId(), row.getKind());
			return;
		}
		try {
			sender.send(prepared.to(), prepared.subject(), prepared.text());
			row.setStatus(ExamOutboundMail.SENT);
			row.setUpdatedAt(now);
		}
		catch (RuntimeException exception) {
			int attempts = row.getAttemptCount() + 1;
			row.setAttemptCount(attempts);
			if (attempts >= MAX_ATTEMPTS) {
				row.setStatus(ExamOutboundMail.FAILED);
			}
			row.setUpdatedAt(now);
			log.warn("exam mail send failed id={} attempt={} error={}", row.getId(), attempts,
					exception.getClass().getSimpleName());
		}
	}

}
