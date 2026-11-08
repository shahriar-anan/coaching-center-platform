package com.coachingcenter.api.exams;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Walks the category tree downwards. */
final class LiveExamCategoryTree {

	private LiveExamCategoryTree() {
	}

	/** The category and every category under it, at any depth. An id the table does not know gives just itself, so it matches no exam. */
	static Set<UUID> categoryAndDescendants(UUID categoryId, Collection<LiveExamCategory> all) {
		Map<UUID, List<UUID>> children = new HashMap<>();
		for (LiveExamCategory category : all) {
			if (category.getParentId() != null) {
				children.computeIfAbsent(category.getParentId(), key -> new ArrayList<>()).add(category.getId());
			}
		}
		Set<UUID> found = new HashSet<>();
		Deque<UUID> pending = new ArrayDeque<>();
		pending.push(categoryId);
		while (!pending.isEmpty()) {
			UUID current = pending.pop();
			if (found.add(current)) {
				pending.addAll(children.getOrDefault(current, List.of()));
			}
		}
		return found;
	}

}
