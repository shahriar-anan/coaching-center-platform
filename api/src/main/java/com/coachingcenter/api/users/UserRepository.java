package com.coachingcenter.api.users;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByPhoneAndDeletedAtIsNull(String phone);

	Optional<User> findByEmailAndDeletedAtIsNull(String email);

	Optional<User> findByGoogleSubAndDeletedAtIsNull(String googleSub);

	Optional<User> findByIdAndDeletedAtIsNull(UUID id);

	boolean existsByRoleAndDeletedAtIsNull(String role);

	Page<User> findByRoleAndDeletedAtIsNull(String role, Pageable pageable);

	@Query(value = """
			select u.* from users u
			left join student_profiles p on p.user_id = u.id
			where u.deleted_at is null and u.role = 'STUDENT'
			and (:q = '' or u.phone ilike concat('%', :q, '%') or u.email ilike concat('%', :q, '%')
				or p.full_name ilike concat('%', :q, '%') or p.student_code ilike concat('%', :q, '%'))
			""", countQuery = """
			select count(*) from users u
			left join student_profiles p on p.user_id = u.id
			where u.deleted_at is null and u.role = 'STUDENT'
			and (:q = '' or u.phone ilike concat('%', :q, '%') or u.email ilike concat('%', :q, '%')
				or p.full_name ilike concat('%', :q, '%') or p.student_code ilike concat('%', :q, '%'))
			""", nativeQuery = true)
	Page<User> searchStudents(@Param("q") String q, Pageable pageable);

	@Query(value = """
			select u.* from users u
			left join admin_profiles p on p.user_id = u.id
			where u.deleted_at is null and u.role = 'INSTRUCTOR'
			and (:status = '' or u.status = :status)
			and (:assigned = '' or (:assigned = 'true') = exists (
				select 1 from live_exam_instructors i join live_exam_modules m on m.id = i.module_id
				where i.instructor_id = u.id and m.deleted_at is null))
			and (:q = '' or u.phone ilike concat('%', :q, '%') or u.email ilike concat('%', :q, '%')
				or p.full_name ilike concat('%', :q, '%'))
			order by lower(p.full_name), u.id
			""", countQuery = """
			select count(*) from users u
			left join admin_profiles p on p.user_id = u.id
			where u.deleted_at is null and u.role = 'INSTRUCTOR'
			and (:status = '' or u.status = :status)
			and (:assigned = '' or (:assigned = 'true') = exists (
				select 1 from live_exam_instructors i join live_exam_modules m on m.id = i.module_id
				where i.instructor_id = u.id and m.deleted_at is null))
			and (:q = '' or u.phone ilike concat('%', :q, '%') or u.email ilike concat('%', :q, '%')
				or p.full_name ilike concat('%', :q, '%'))
			""", nativeQuery = true)
	Page<User> searchInstructors(@Param("q") String q, @Param("status") String status,
			@Param("assigned") String assigned, Pageable pageable);

	@Query("""
			SELECT u FROM User u
			WHERE u.deletedAt IS NULL AND u.role = 'STUDENT' AND lower(u.email) = lower(:email)
			""")
	Optional<User> findStudentByEmail(@Param("email") String email);

	@Query("""
			SELECT u FROM User u, StudentProfile p
			WHERE p.userId = u.id AND u.deletedAt IS NULL AND u.role = 'STUDENT'
			  AND lower(p.fullName) LIKE lower(concat('%', :name, '%'))
			ORDER BY p.fullName, u.id
			""")
	java.util.List<User> findStudentsByName(@Param("name") String name);

}
