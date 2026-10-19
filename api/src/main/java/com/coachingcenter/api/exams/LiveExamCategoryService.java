package com.coachingcenter.api.exams;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import tools.jackson.databind.JsonNode;

@Service
public class LiveExamCategoryService {

	private final LiveExamCategoryRepository categories;

	private final LiveExamModuleRepository modules;

	private final LiveExamCatalogVisibility visibility;

	private final AuditRecorder audit;

	public LiveExamCategoryService(LiveExamCategoryRepository categories, LiveExamModuleRepository modules,
			LiveExamCatalogVisibility visibility, AuditRecorder audit) {
		this.categories = categories;
		this.modules = modules;
		this.visibility = visibility;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<TreeNode> tree() {
		Map<UUID, List<ModuleSummary>> modulesByCategory = new HashMap<>();
		for (LiveExamModule module : modules.findByDeletedAtIsNull()) {
			modulesByCategory.computeIfAbsent(module.getCategoryId(), ignored -> new ArrayList<>())
				.add(summary(module));
		}
		Map<UUID, TreeNode> nodes = new HashMap<>();
		List<TreeNode> roots = new ArrayList<>();
		for (LiveExamCategory category : categories.findByDeletedAtIsNull()) {
			List<ModuleSummary> placed = new ArrayList<>(
					modulesByCategory.getOrDefault(category.getId(), List.of()));
			placed.sort(Comparator.comparingInt(ModuleSummary::position).thenComparing(ModuleSummary::id));
			TreeNode node = new TreeNode(category.getId(), category.getParentId(), category.getTitleEn(),
					category.getTitleBn(), category.getPosition(), category.getArchivedAt() != null, new ArrayList<>(),
					placed);
			nodes.put(category.getId(), node);
		}
		for (TreeNode node : nodes.values()) {
			if (node.parentId() == null) {
				roots.add(node);
			}
			else {
				TreeNode parent = nodes.get(node.parentId());
				if (parent != null) {
					parent.children().add(node);
				}
			}
		}
		sort(roots);
		return roots;
	}

	@Transactional(readOnly = true)
	public CategoryView get(UUID id) {
		return view(require(id));
	}

	@Transactional
	public CategoryView create(JsonNode body) {
		String titleEn = requiredText(body, "titleEn");
		String titleBn = requiredText(body, "titleBn");
		UUID parentId = readParent(body);
		if (parentId != null) {
			visibility.requireOpen(parentId);
		}
		int position = readPosition(body, parentId, null);
		LiveExamCategory category = new LiveExamCategory();
		category.setParentId(parentId);
		category.setTitleEn(titleEn);
		category.setTitleBn(titleBn);
		category.setPosition(position);
		return view(categories.save(category));
	}

	@Transactional
	public CategoryView update(UUID id, JsonNode body) {
		LiveExamCategory category = require(id);
		if (body.has("titleEn") && !body.get("titleEn").isNull()) {
			category.setTitleEn(requiredText(body, "titleEn"));
		}
		if (body.has("titleBn") && !body.get("titleBn").isNull()) {
			category.setTitleBn(requiredText(body, "titleBn"));
		}
		boolean parentChanged = false;
		if (body.has("parentId")) {
			UUID parentId = readParent(body);
			if (parentId != null) {
				visibility.requireOpen(parentId);
			}
			if (cycle(id, parentId)) {
				throw new ApiException(ErrorCode.CATEGORY_CYCLE, HttpStatus.BAD_REQUEST,
						"Category parent would create a cycle.");
			}
			category.setParentId(parentId);
			parentChanged = true;
		}
		if (body.has("position") && !body.get("position").isNull()) {
			category.setPosition(readPosition(body, category.getParentId(), id));
		}
		else if (parentChanged) {
			category.setPosition(nextPosition(category.getParentId(), id));
		}
		return view(category);
	}

	@Transactional
	public void delete(UUID id) {
		LiveExamCategory category = require(id);
		rejectWhenOccupied(id);
		category.setDeletedAt(Instant.now());
		audit.record(AuditRecorder.LIVE_EXAM_CATEGORY_DELETE, AuditRecorder.LIVE_EXAM_CATEGORY_ENTITY,
				category.getId());
	}

	@Transactional
	public void archive(UUID id) {
		LiveExamCategory category = require(id);
		if (category.getArchivedAt() != null) {
			return;
		}
		category.setArchivedAt(Instant.now());
		audit.record(AuditRecorder.LIVE_EXAM_CATEGORY_ARCHIVE, AuditRecorder.LIVE_EXAM_CATEGORY_ENTITY,
				category.getId());
	}

	private void rejectWhenOccupied(UUID id) {
		if (!categories.findByParentIdAndDeletedAtIsNull(id).isEmpty()
				|| !modules.findByCategoryIdAndDeletedAtIsNull(id).isEmpty()) {
			throw new ApiException(ErrorCode.CATEGORY_NOT_EMPTY, HttpStatus.CONFLICT,
					"Category still has a subcategory or a module.");
		}
	}

	private int readPosition(JsonNode body, UUID parentId, UUID ignoreId) {
		int position = body.has("position") && !body.get("position").isNull() ? body.get("position").asInt()
				: nextPosition(parentId, ignoreId);
		if (positionTaken(parentId, position, ignoreId)) {
			throw new ApiException(ErrorCode.CATEGORY_POSITION_TAKEN, HttpStatus.CONFLICT,
					"That position is already used under this category.");
		}
		return position;
	}

	private int nextPosition(UUID parentId, UUID ignoreId) {
		int max = -1;
		for (LiveExamCategory sibling : siblings(parentId)) {
			if (ignoreId == null || !sibling.getId().equals(ignoreId)) {
				max = Math.max(max, sibling.getPosition());
			}
		}
		return max + 1;
	}

	private boolean positionTaken(UUID parentId, int position, UUID ignoreId) {
		for (LiveExamCategory sibling : siblings(parentId)) {
			if (sibling.getPosition() == position && (ignoreId == null || !sibling.getId().equals(ignoreId))) {
				return true;
			}
		}
		return false;
	}

	private List<LiveExamCategory> siblings(UUID parentId) {
		if (parentId == null) {
			return categories.findByParentIdIsNullAndDeletedAtIsNull();
		}
		return categories.findByParentIdAndDeletedAtIsNull(parentId);
	}

	private boolean cycle(UUID categoryId, UUID newParentId) {
		UUID current = newParentId;
		while (current != null) {
			if (current.equals(categoryId)) {
				return true;
			}
			current = categories.findByIdAndDeletedAtIsNull(current).map(LiveExamCategory::getParentId).orElse(null);
		}
		return false;
	}

	private LiveExamCategory require(UUID id) {
		return categories.findByIdAndDeletedAtIsNull(id).orElseThrow(this::notFound);
	}

	private static UUID readParent(JsonNode body) {
		if (!body.has("parentId") || body.get("parentId").isNull()) {
			return null;
		}
		return UUID.fromString(body.get("parentId").asText());
	}

	private static String requiredText(JsonNode body, String field) {
		JsonNode node = body.get(field);
		if (node == null || node.isNull() || node.asText().isBlank()) {
			throw validationFailed();
		}
		return node.asText().trim();
	}

	private static void sort(List<TreeNode> nodes) {
		nodes.sort(Comparator.comparingInt(TreeNode::position).thenComparing(TreeNode::id));
		for (TreeNode node : nodes) {
			sort(node.children());
		}
	}

	private static CategoryView view(LiveExamCategory category) {
		return new CategoryView(category.getId(), category.getParentId(), category.getTitleEn(), category.getTitleBn(),
				category.getPosition(), category.getArchivedAt() != null);
	}

	private static ModuleSummary summary(LiveExamModule module) {
		return new ModuleSummary(module.getId(), module.getCategoryId(), module.getTitleEn(), module.getTitleBn(),
				module.getStatus(), module.getPosition());
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	public record CategoryView(UUID id, UUID parentId, String titleEn, String titleBn, int position, boolean archived) {
	}

	public record ModuleSummary(UUID id, UUID categoryId, String titleEn, String titleBn, String status, int position) {
	}

	public record TreeNode(UUID id, UUID parentId, String titleEn, String titleBn, int position, boolean archived,
			List<TreeNode> children, List<ModuleSummary> modules) {
	}

}
