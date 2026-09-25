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

	Optional<User> findByIdAndDeletedAtIsNull(UUID id);

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

}
