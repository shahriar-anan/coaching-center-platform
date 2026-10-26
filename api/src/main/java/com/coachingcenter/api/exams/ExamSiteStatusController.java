package com.coachingcenter.api.exams;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public switches the website reads before it offers checkout. Names only, never a configuration value. */
@RestController
public class ExamSiteStatusController {

	private final ObjectProvider<LiveExamPaymentGateway> gateways;

	public ExamSiteStatusController(ObjectProvider<LiveExamPaymentGateway> gateways) {
		this.gateways = gateways;
	}

	/** {@code checkoutEnabled} is true when the payment gateway is configured, the same test the order call makes. */
	@GetMapping("/api/v1/public/exam-site/status")
	public Status status() {
		return new Status(gateways.getIfAvailable() != null);
	}

	public record Status(boolean checkoutEnabled) {
	}

}
