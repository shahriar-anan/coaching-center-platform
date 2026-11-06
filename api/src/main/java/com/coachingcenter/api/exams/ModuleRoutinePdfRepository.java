package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Keyed by package id; {@code findAllById} loads a whole page's routine PDFs in one query. */
public interface ModuleRoutinePdfRepository extends JpaRepository<ModuleRoutinePdf, UUID> {
}
