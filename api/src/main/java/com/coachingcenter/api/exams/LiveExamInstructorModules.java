package com.coachingcenter.api.exams;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** Read-only view of which live exam modules each instructor holds, for the instructor directory. */
@Service
public class LiveExamInstructorModules {

	@PersistenceContext
	private EntityManager entityManager;

	@Transactional(readOnly = true)
	public Map<UUID, List<ModuleRef>> modulesFor(Collection<UUID> instructorIds) {
		Map<UUID, List<ModuleRef>> result = new HashMap<>();
		if (instructorIds.isEmpty()) {
			return result;
		}
		List<Object[]> rows = entityManager.createQuery("""
				select i.instructorId, m.id, m.titleEn, m.titleBn, m.status
				from LiveExamInstructor i, LiveExamModule m
				where m.id = i.moduleId and m.deletedAt is null and i.instructorId in :ids
				order by m.titleEn, m.id
				""", Object[].class).setParameter("ids", instructorIds).getResultList();
		for (Object[] row : rows) {
			result.computeIfAbsent((UUID) row[0], key -> new java.util.ArrayList<>())
				.add(new ModuleRef((UUID) row[1], (String) row[2], (String) row[3], (String) row[4]));
		}
		return result;
	}

	public record ModuleRef(UUID id, String titleEn, String titleBn, String status) {
	}

}
