package com.coachingcenter.api.users;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.PhoneNumbers;
import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.common.config.AppProperties;

@Component
public class MasterAdminBootstrap implements ApplicationRunner {

	private final UserRepository users;

	private final AdminProfileRepository adminProfiles;

	private final PasswordEncoder passwords;

	private final AppProperties properties;

	public MasterAdminBootstrap(UserRepository users, AdminProfileRepository adminProfiles, PasswordEncoder passwords,
			AppProperties properties) {
		this.users = users;
		this.adminProfiles = adminProfiles;
		this.passwords = passwords;
		this.properties = properties;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (users.existsByRoleAndDeletedAtIsNull(Role.MASTER_ADMIN.name())) {
			return;
		}
		AppProperties.Bootstrap bootstrap = properties.bootstrap();
		if (blank(bootstrap.phone()) || blank(bootstrap.email()) || blank(bootstrap.name()) || blank(bootstrap.password())) {
			throw new IllegalStateException(
					"MASTER_ADMIN_PHONE, MASTER_ADMIN_EMAIL, MASTER_ADMIN_NAME, and MASTER_ADMIN_PASSWORD are required when no Master Admin exists.");
		}
		User user = new User();
		user.setPhone(PhoneNumbers.normalize(bootstrap.phone()));
		user.setEmail(bootstrap.email().trim().toLowerCase());
		user.setPasswordHash(passwords.encode(bootstrap.password()));
		user.setRole(Role.MASTER_ADMIN.name());
		user.setStatus("ACTIVE");
		users.save(user);
		AdminProfile profile = new AdminProfile();
		profile.setUserId(user.getId());
		profile.setFullName(bootstrap.name().trim());
		adminProfiles.save(profile);
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

}
