package com.coachingcenter.api.exams;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

@Component
public class LiveExamCatalogVisibility {

	private final LiveExamCategoryRepository categories;

	public LiveExamCatalogVisibility(LiveExamCategoryRepository categories) {
		this.categories = categories;
	}

	public boolean branchHidden(UUID categoryId) {
		return hiddenCategoryIds().contains(categoryId);
	}

	public Set<UUID> hiddenCategoryIds() {
		List<LiveExamCategory> all = categories.findAll();
		Map<UUID, LiveExamCategory> byId = new HashMap<>();
		for (LiveExamCategory category : all) {
			byId.put(category.getId(), category);
		}
		Set<UUID> hidden = new HashSet<>();
		for (LiveExamCategory category : all) {
			if (hiddenWalk(category.getId(), byId, new HashSet<>())) {
				hidden.add(category.getId());
			}
		}
		return hidden;
	}

	public void requireOpen(UUID categoryId) {
		categories.findByIdAndDeletedAtIsNull(categoryId).orElseThrow(this::notFound);
		if (branchHidden(categoryId)) {
			throw new ApiException(ErrorCode.CATEGORY_ARCHIVED, HttpStatus.CONFLICT,
					"An archived category cannot receive a category or a module.");
		}
	}

	private boolean hiddenWalk(UUID id, Map<UUID, LiveExamCategory> byId, Set<UUID> seen) {
		if (id == null) {
			return false;
		}
		if (!seen.add(id)) {
			return true;
		}
		LiveExamCategory category = byId.get(id);
		if (category == null || category.getDeletedAt() != null || category.getArchivedAt() != null) {
			return true;
		}
		return hiddenWalk(category.getParentId(), byId, seen);
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

}
