package com.coachingcenter.api.auth;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

interface OutboundMail {

	MimeMessage createMimeMessage() throws MessagingException;

	String send(MimeMessage message) throws MessagingException;

}
