package com.coachingcenter.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.coachingcenter.api.common.config.AppProperties;
import com.coachingcenter.api.files.FileStorageProperties;
import com.coachingcenter.api.purchases.PaymentProperties;

@SpringBootApplication
@EnableConfigurationProperties({ AppProperties.class, FileStorageProperties.class, PaymentProperties.class })
public class ApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiApplication.class, args);
	}

}
