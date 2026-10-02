package com.coachingcenter.api.users;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {

	boolean existsByStudentCode(String studentCode);

}
