package com.coachingcenter.api.purchases;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(String bkashNumber, String nagadNumber) {
}
