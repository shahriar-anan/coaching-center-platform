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
 * contains at least one exam assigned to the instructor (D6), or when the instructor is the package's default instructor.
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
			add(result, (UUID) row[0], new ModuleRef((UUID) row[1], (String) row[2], (String) row[3], (String) row[4]));
		}
		// A package whose default instructor is this person counts too, even before it has an exam assigned to them.
		List<Object[]> defaults = entityManager.createQuery("""
				select i.instructorId, m.id, m.titleEn, m.titleBn, m.status
				from LiveExamInstructor i, LiveExamModule m
				where i.instructorId in :ids and m.id = i.moduleId and m.deletedAt is null
				""", Object[].class).setParameter("ids", instructorIds).getResultList();
		for (Object[] row : defaults) {
			add(result, (UUID) row[0], new ModuleRef((UUID) row[1], (String) row[2], (String) row[3], (String) row[4]));
		}
		result.values().forEach(list -> list.sort(java.util.Comparator.comparing(ModuleRef::titleEn).thenComparing(ModuleRef::id)));
		return result;
	}

	private static void add(Map<UUID, List<ModuleRef>> result, UUID instructorId, ModuleRef ref) {
		List<ModuleRef> list = result.computeIfAbsent(instructorId, key -> new java.util.ArrayList<>());
		if (list.stream().noneMatch(existing -> existing.id().equals(ref.id()))) {
			list.add(ref);
		}
	}

	public record ModuleRef(UUID id, String titleEn, String titleBn, String status) {
	}

}
