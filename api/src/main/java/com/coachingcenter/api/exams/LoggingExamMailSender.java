package com.coachingcenter.api.exams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class LoggingExamMailSender implements ExamMailSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingExamMailSender.class);

	@Override
	public void send(String to, String subject, String text) {
		log.info("Exam message queued");
	}

}
