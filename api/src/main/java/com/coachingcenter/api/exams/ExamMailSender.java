package com.coachingcenter.api.exams;

public interface ExamMailSender {

	void send(String to, String subject, String text);

}
