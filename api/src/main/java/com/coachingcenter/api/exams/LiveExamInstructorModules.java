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

/**
 * Read-only view of which live exam packages each instructor holds, for the instructor directory. A package counts when it
 * contains at least one exam assigned to the instructor (D6).
 */
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
				select distinct e.instructorId, m.id, m.titleEn, m.titleBn, m.status
				from LiveExam e, LiveExamPackageExam pe, LiveExamModule m
				where e.instructorId in :ids and e.deletedAt is null and pe.examId = e.id and pe.removedAt is null
				  and m.id = pe.moduleId and m.deletedAt is null
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
