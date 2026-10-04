package com.coachingcenter.api.courses;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import tools.jackson.databind.JsonNode;

@Service
public class CourseCategoryService {

	private final CourseCategoryRepository categories;

	private final CourseRepository courses;

	private final AuditRecorder audit;

	public CourseCategoryService(CourseCategoryRepository categories, CourseRepository courses, AuditRecorder audit) {
		this.categories = categories;
		this.courses = courses;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<BrowseCategoryNode> browseTree() {
		Map<UUID, BrowseCategoryNode> nodes = new HashMap<>();
		List<BrowseCategoryNode> roots = new ArrayList<>();
		for (CourseCategory category : categories.findByDeletedAtIsNull()) {
			BrowseCategoryNode node = new BrowseCategoryNode(category.getId(), category.getParentId(),
					category.getTitleEn(), category.getTitleBn(), category.getPosition(), new ArrayList<>());
			nodes.put(category.getId(), node);
		}
		for (BrowseCategoryNode node : nodes.values()) {
			if (node.parentId() == null) {
				roots.add(node);
			}
			else {
				BrowseCategoryNode parent = nodes.get(node.parentId());
				if (parent != null) {
					parent.children().add(node);
				}
			}
		}
		sortBrowse(roots);
		return roots;
	}

	@Transactional(readOnly = true)
	public Set<UUID> selfAndDescendantCategoryIds(UUID categoryId) {
		require(categoryId);
		Set<UUID> ids = new HashSet<>();
		collectDescendants(categoryId, ids);
		return ids;
	}

	@Transactional(readOnly = true)
	public List<CategoryPathItem> pathToCategory(UUID categoryId) {
		if (categoryId == null) {
			return List.of();
		}
		List<CategoryPathItem> path = new ArrayList<>();
		UUID current = categoryId;
		while (current != null) {
			CourseCategory category = categories.findByIdAndDeletedAtIsNull(current).orElse(null);
			if (category == null) {
				break;
			}
			path.add(0, new CategoryPathItem(category.getTitleEn(), category.getTitleBn()));
			current = category.getParentId();
		}
		return path;
	}

	@Transactional(readOnly = true)
	public List<TreeNode> tree() {
		Map<UUID, TreeNode> nodes = new HashMap<>();
		List<TreeNode> roots = new ArrayList<>();
		for (CourseCategory category : categories.findByDeletedAtIsNull()) {
			TreeNode node = new TreeNode("CATEGORY", category.getId(), category.getParentId(), category.getTitleEn(),
					category.getTitleBn(), category.getPosition(), new ArrayList<>());
			nodes.put(category.getId(), node);
		}
		for (TreeNode node : nodes.values()) {
			if (node.parentId() == null) {
				roots.add(node);
			}
			else {
				TreeNode parent = nodes.get(node.parentId());
				if (parent != null && parent.children() != null) {
					parent.children().add(node);
				}
			}
		}
		for (Course course : courses.findByCategoryIdIsNotNullAndDeletedAtIsNull()) {
			TreeNode parent = nodes.get(course.getCategoryId());
			if (parent != null && parent.children() != null && course.getCategoryPosition() != null) {
				parent.children().add(new TreeNode("COURSE", course.getId(), null, course.getTitleEn(),
						course.getTitleBn(), course.getCategoryPosition(), null));
			}
		}
		sort(roots);
		return roots;
	}

	@Transactional
	public CategoryView create(JsonNode body) {
		String titleEn = requiredText(body, "titleEn");
		String titleBn = requiredText(body, "titleBn");
		UUID parentId = readParent(body);
		if (parentId != null) {
			require(parentId);
		}
		int position = readPosition(body, parentId, null);
		CourseCategory category = new CourseCategory();
		category.setParentId(parentId);
		category.setTitleEn(titleEn);
		category.setTitleBn(titleBn);
		category.setPosition(position);
		return view(categories.save(category));
	}

	@Transactional
	public CategoryView update(UUID id, JsonNode body) {
		CourseCategory category = require(id);
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
				require(parentId);
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
		CourseCategory category = require(id);
		if (!categories.findByParentIdAndDeletedAtIsNull(id).isEmpty()
				|| !courses.findByCategoryIdAndDeletedAtIsNull(id).isEmpty()) {
			throw new ApiException(ErrorCode.CATEGORY_NOT_EMPTY, HttpStatus.CONFLICT,
					"Category still has a subcategory or a course.");
		}
		category.setDeletedAt(Instant.now());
		audit.record(AuditRecorder.COURSE_CATEGORY_DELETE, AuditRecorder.COURSE_CATEGORY_ENTITY, category.getId());
	}

	@Transactional
	public void assign(Course course, JsonNode body) {
		boolean hasCategory = body.has("categoryId");
		boolean hasPosition = body.has("categoryPosition") && !body.get("categoryPosition").isNull();
		if (hasCategory && body.get("categoryId").isNull()) {
			if (hasPosition) {
				throw validationFailed();
			}
			course.setCategoryId(null);
			course.setCategoryPosition(null);
			return;
		}
		UUID categoryId = hasCategory ? UUID.fromString(body.get("categoryId").asText()) : course.getCategoryId();
		if (categoryId == null) {
			throw validationFailed();
		}
		require(categoryId);
		int position = hasPosition ? body.get("categoryPosition").asInt() : nextPosition(categoryId, null);
		if (positionTaken(categoryId, position, null, course.getId())) {
			throw new ApiException(ErrorCode.CATEGORY_POSITION_TAKEN, HttpStatus.CONFLICT,
					"That position is already used under this category.");
		}
		course.setCategoryId(categoryId);
		course.setCategoryPosition(position);
	}

	private int readPosition(JsonNode body, UUID parentId, UUID ignoreCategoryId) {
		int position = body.has("position") && !body.get("position").isNull() ? body.get("position").asInt()
				: nextPosition(parentId, ignoreCategoryId);
		if (positionTaken(parentId, position, ignoreCategoryId, null)) {
			throw new ApiException(ErrorCode.CATEGORY_POSITION_TAKEN, HttpStatus.CONFLICT,
					"That position is already used under this category.");
		}
		return position;
	}

	private int nextPosition(UUID parentId, UUID ignoreCategoryId) {
		int max = -1;
		for (CourseCategory sibling : siblings(parentId)) {
			if (!sibling.getId().equals(ignoreCategoryId)) {
				max = Math.max(max, sibling.getPosition());
			}
		}
		if (parentId != null) {
			for (Course course : courses.findByCategoryIdAndDeletedAtIsNull(parentId)) {
				if (course.getCategoryPosition() != null) {
					max = Math.max(max, course.getCategoryPosition());
				}
			}
		}
		return max + 1;
	}

	private boolean positionTaken(UUID parentId, int position, UUID ignoreCategoryId, UUID ignoreCourseId) {
		for (CourseCategory sibling : siblings(parentId)) {
			if (sibling.getPosition() == position && !sibling.getId().equals(ignoreCategoryId)) {
				return true;
			}
		}
		if (parentId != null) {
			for (Course course : courses.findByCategoryIdAndDeletedAtIsNull(parentId)) {
				if (course.getCategoryPosition() != null && course.getCategoryPosition() == position
						&& !course.getId().equals(ignoreCourseId)) {
					return true;
				}
			}
		}
		return false;
	}

	private List<CourseCategory> siblings(UUID parentId) {
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
			current = categories.findByIdAndDeletedAtIsNull(current).map(CourseCategory::getParentId).orElse(null);
		}
		return false;
	}

	private CourseCategory require(UUID id) {
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

	private void collectDescendants(UUID categoryId, Set<UUID> ids) {
		ids.add(categoryId);
		for (CourseCategory child : categories.findByParentIdAndDeletedAtIsNull(categoryId)) {
			collectDescendants(child.getId(), ids);
		}
	}

	private static void sortBrowse(List<BrowseCategoryNode> nodes) {
		nodes.sort(Comparator.comparingInt(BrowseCategoryNode::position));
		for (BrowseCategoryNode node : nodes) {
			sortBrowse(node.children());
		}
	}

	private static void sort(List<TreeNode> nodes) {
		nodes.sort(Comparator.comparingInt(TreeNode::position));
		for (TreeNode node : nodes) {
			if (node.children() != null) {
				sort(node.children());
			}
		}
	}

	private static CategoryView view(CourseCategory category) {
		return new CategoryView(category.getId(), category.getParentId(), category.getTitleEn(), category.getTitleBn(),
				category.getPosition());
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	public record CategoryView(UUID id, UUID parentId, String titleEn, String titleBn, int position) {
	}

	public record TreeNode(String kind, UUID id, UUID parentId, String titleEn, String titleBn, int position,
			List<TreeNode> children) {
	}

	public record BrowseCategoryNode(UUID id, UUID parentId, String titleEn, String titleBn, int position,
			List<BrowseCategoryNode> children) {
	}

	public record CategoryPathItem(String titleEn, String titleBn) {
	}

}
