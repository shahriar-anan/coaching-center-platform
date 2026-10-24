package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.PhoneNumbers;
import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.users.AdminProfile;
import com.coachingcenter.api.users.AdminProfileRepository;
import com.coachingcenter.api.users.StudentProfile;
import com.coachingcenter.api.users.StudentProfileRepository;
import com.coachingcenter.api.users.User;
import com.coachingcenter.api.users.UserRepository;

@Component
@Profile({ "staging", "dev" })
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class StagingExamSeed implements ApplicationRunner {

	static final String EXAM_TITLE = "Staging practice exam";

	private static final String STUDENT_CODE = "SEED-STUDENT";

	private final Environment environment;

	private final UserRepository users;

	private final AdminProfileRepository adminProfiles;

	private final StudentProfileRepository studentProfiles;

	private final LiveExamCategoryRepository categories;

	private final LiveExamModuleRepository modules;

	private final LiveExamRepository exams;

	private final PasswordEncoder passwords;

	public StagingExamSeed(Environment environment, UserRepository users, AdminProfileRepository adminProfiles,
			StudentProfileRepository studentProfiles, LiveExamCategoryRepository categories,
			LiveExamModuleRepository modules, LiveExamRepository exams, PasswordEncoder passwords) {
		this.environment = environment;
		this.users = users;
		this.adminProfiles = adminProfiles;
		this.studentProfiles = studentProfiles;
		this.categories = categories;
		this.modules = modules;
		this.exams = exams;
		this.passwords = passwords;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		refuseProduction(environment.getActiveProfiles());
		Account admin = account("SEED_ADMIN");
		Account student = account("SEED_STUDENT");
		ensureUser(admin, Role.SYSTEM_ADMIN, false);
		ensureUser(student, Role.STUDENT, true);
		ensureExam();
	}

	static void refuseProduction(String[] profiles) {
		if (Arrays.stream(profiles).anyMatch(profile -> "prod".equalsIgnoreCase(profile))) {
			throw new IllegalStateException("The staging seed cannot run on the prod profile.");
		}
	}

	static String required(String name, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(name + " is required.");
		}
		return value.trim();
	}

	static void requirePassword(String name, String password) {
		if (password.length() < 8 || password.length() > 72) {
			throw new IllegalStateException(name + " must be 8 to 72 characters.");
		}
	}

	private Account account(String prefix) {
		String phoneName = prefix + "_PHONE";
		String emailName = prefix + "_EMAIL";
		String nameName = prefix + "_NAME";
		String passwordName = prefix + "_PASSWORD";
		String password = required(passwordName, environment.getProperty(passwordName));
		requirePassword(passwordName, password);
		String phone;
		try {
			phone = PhoneNumbers.normalize(required(phoneName, environment.getProperty(phoneName)));
		}
		catch (ApiException exception) {
			throw new IllegalStateException(phoneName + " is not a valid phone number.");
		}
		return new Account(phone, required(emailName, environment.getProperty(emailName)).toLowerCase(),
				required(nameName, environment.getProperty(nameName)), password, phoneName);
	}

	private void ensureUser(Account account, Role role, boolean student) {
		if (users.findByEmailAndDeletedAtIsNull(account.email()).isPresent()) {
			return;
		}
		if (users.findByPhoneAndDeletedAtIsNull(account.phone()).isPresent()) {
			throw new IllegalStateException(account.phoneName() + " is already in use.");
		}
		User user = new User();
		user.setPhone(account.phone());
		user.setEmail(account.email());
		user.setPasswordHash(passwords.encode(account.password()));
		user.setRole(role.name());
		user.setStatus("ACTIVE");
		user.setEmailVerifiedAt(Instant.now());
		users.save(user);
		if (student) {
			if (studentProfiles.existsByStudentCode(STUDENT_CODE)) {
				throw new IllegalStateException(STUDENT_CODE + " is already in use.");
			}
			StudentProfile profile = new StudentProfile();
			profile.setUserId(user.getId());
			profile.setFullName(account.name());
			profile.setStudentCode(STUDENT_CODE);
			studentProfiles.save(profile);
			return;
		}
		AdminProfile profile = new AdminProfile();
		profile.setUserId(user.getId());
		profile.setFullName(account.name());
		adminProfiles.save(profile);
	}

	private void ensureExam() {
		if (exams.findByTitleEnAndDeletedAtIsNull(EXAM_TITLE).isPresent()) {
			return;
		}
		LiveExamCategory category = new LiveExamCategory();
		category.setTitleEn("Staging practice");
		category.setTitleBn("Staging practice");
		category.setPosition(0);
		categories.save(category);

		LiveExamModule module = new LiveExamModule();
		module.setCategoryId(category.getId());
		module.setTitleEn("Staging practice module");
		module.setTitleBn("Staging practice module");
		module.setDescriptionEn("Practice");
		module.setDescriptionBn("Practice");
		module.setAccessMode(LiveExamModule.MANUAL);
		module.setStatus(LiveExamModule.PUBLISHED);
		module.setFinallySaved(true);
		module.setPosition(0);
		module.setPrice(new BigDecimal("10.00"));
		modules.save(module);

		LiveExam exam = new LiveExam();
		exam.setModuleId(module.getId());
		exam.setExamType(LiveExam.MCQ);
		exam.setTitleEn(EXAM_TITLE);
		exam.setTitleBn(EXAM_TITLE);
		exam.setDescriptionEn("Practice");
		exam.setDescriptionBn("Practice");
		exam.setRulesEn("Practice");
		exam.setRulesBn("Practice");
		exam.setPaperLanguage(LiveExam.EN);
		exam.setDurationMinutes(30);
		exam.setStatus(LiveExam.PUBLISHED);
		exam.setFinallySaved(true);
		exam.setPrice(new BigDecimal("10.00"));
		exams.save(exam);
	}

	private record Account(String phone, String email, String name, String password, String phoneName) {
	}

}
