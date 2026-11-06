package com.coachingcenter.api.users;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentPhotoRepository extends JpaRepository<StudentPhoto, UUID> {

}
