package com.coachingcenter.api.courses;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import tools.jackson.databind.JsonNode;

@Service
public class ContentNodeService {

	private static final Set<String> NODE_TYPES = Set.of("SECTION", "LECTURE");

	private final CourseRepository courses;

	private final ContentNodeRepository nodes;

	private final CourseMaterialService materials;

	public ContentNodeService(CourseRepository courses, ContentNodeRepository nodes, CourseMaterialService materials) {
		this.courses = courses;
		this.nodes = nodes;
		this.materials = materials;
	}

	@Transactional
	public NodeView create(UUID courseId, JsonNode body) {
		Course course = requireCourse(courseId);
		String nodeType = requiredText(body, "nodeType");
		validateNodeType(nodeType);
		UUID parentId = readParentId(body);
		ContentNode parent = validateParent(courseId, parentId, nodeType);
		if (parent != null && "LECTURE".equals(parent.getNodeType())) {
			throw nodeTypeRejected();
		}
		String title = requiredText(body, "title");
		boolean free = body.has("free") && !body.get("free").isNull() && body.get("free").asBoolean();
		if ("LECTURE".equals(nodeType) && "FREEMIUM".equals(course.getAccessType()) && free) {
			// allowed
		}
		int position = nextPosition(courseId, parentId);
		if (body.has("position") && !body.get("position").isNull()) {
			position = body.get("position").asInt();
		}
		ContentNode node = new ContentNode();
		node.setCourseId(courseId);
		node.setParentId(parentId);
		node.setNodeType(nodeType);
		node.setTitle(title);
		node.setPosition(position);
		node.setFree(free && "FREEMIUM".equals(course.getAccessType()) && "LECTURE".equals(nodeType));
		return view(nodes.save(node));
	}

	@Transactional
	public NodeView update(UUID courseId, UUID nodeId, JsonNode body) {
		Course course = requireCourse(courseId);
		ContentNode node = requireNode(courseId, nodeId);
		if (body.has("title") && !body.get("title").isNull()) {
			node.setTitle(requiredText(body, "title"));
		}
		if (body.has("nodeType") && !body.get("nodeType").isNull()) {
			String nodeType = requiredText(body, "nodeType");
			validateNodeType(nodeType);
			if (!nodeType.equals(node.getNodeType())) {
				throw nodeTypeRejected();
			}
		}
		if (body.has("parentId")) {
			UUID newParentId = readParentId(body);
			if (newParentId != null && newParentId.equals(nodeId)) {
				throw nodeTypeRejected();
			}
			validateParent(courseId, newParentId, node.getNodeType());
			if (newParentId != null) {
				ContentNode newParent = requireNode(courseId, newParentId);
				if ("LECTURE".equals(newParent.getNodeType())) {
					throw nodeTypeRejected();
				}
				if (createsCycle(nodeId, newParentId)) {
					throw nodeTypeRejected();
				}
			}
			int position = nextPosition(courseId, newParentId);
			node.setParentId(newParentId);
			node.setPosition(position);
		}
		if (body.has("free") && !body.get("free").isNull() && "LECTURE".equals(node.getNodeType())) {
			boolean requested = body.get("free").asBoolean();
			if ("FREEMIUM".equals(course.getAccessType())) {
				if (node.isFree() && !requested) {
					throw new ApiException(ErrorCode.FREE_CONTENT_FIXED, HttpStatus.CONFLICT,
							"Free content cannot be made paid.");
				}
				node.setFree(requested);
			}
		}
		if (body.has("videoUrl")) {
			if (!"LECTURE".equals(node.getNodeType())) {
				throw validationFailed();
			}
			if (body.get("videoUrl").isNull() || body.get("videoUrl").asText().isBlank()) {
				node.setVideoProvider(null);
				node.setVideoRef(null);
			}
			else {
				String videoId = YouTubeUrls.parseVideoId(requiredText(body, "videoUrl"));
				if (videoId == null) {
					throw new ApiException(ErrorCode.INVALID_YOUTUBE_URL, HttpStatus.BAD_REQUEST,
							"YouTube URL is invalid.");
				}
				node.setVideoProvider("YOUTUBE");
				node.setVideoRef(videoId);
			}
		}
		if (body.has("notes") && !body.get("notes").isNull()) {
			if (!"LECTURE".equals(node.getNodeType())) {
				throw validationFailed();
			}
			node.setNotes(body.get("notes").asText());
		}
		return view(node);
	}

	@Transactional
	public void reorder(UUID courseId, JsonNode body) {
		requireCourse(courseId);
		UUID parentId = readParentId(body);
		JsonNode ids = body.get("nodeIds");
		if (ids == null || !ids.isArray() || ids.isEmpty()) {
			throw validationFailed();
		}
		List<UUID> ordered = new ArrayList<>();
		for (JsonNode idNode : ids) {
			ordered.add(UUID.fromString(idNode.asText()));
		}
		List<ContentNode> siblings = loadSiblings(courseId, parentId);
		if (siblings.size() != ordered.size()) {
			throw validationFailed();
		}
		Set<UUID> siblingIds = new HashSet<>();
		for (ContentNode sibling : siblings) {
			siblingIds.add(sibling.getId());
		}
		if (!siblingIds.equals(new HashSet<>(ordered))) {
			throw validationFailed();
		}
		Map<UUID, ContentNode> byId = new HashMap<>();
		for (ContentNode sibling : siblings) {
			byId.put(sibling.getId(), sibling);
		}
		for (int index = 0; index < ordered.size(); index++) {
			ContentNode node = byId.get(ordered.get(index));
			node.setPosition(index);
		}
	}

	@Transactional
	public void delete(UUID courseId, UUID nodeId) {
		requireCourse(courseId);
		ContentNode node = requireNode(courseId, nodeId);
		if ("SECTION".equals(node.getNodeType())) {
			if (subtreeHasLecture(courseId, nodeId)) {
				throw new ApiException(ErrorCode.SECTION_NOT_EMPTY, HttpStatus.CONFLICT,
						"Section still has a lecture.");
			}
			deleteEmptySections(courseId, nodeId);
		}
		else {
			materials.deleteForLecture(courseId, nodeId);
		}
		node.setDeletedAt(Instant.now());
	}

	@Transactional(readOnly = true)
	public List<NodeView> list(UUID courseId) {
		requireCourse(courseId);
		return nodes.findByCourseIdAndDeletedAtIsNullOrderByPositionAsc(courseId).stream().map(this::view).toList();
	}

	private int nextPosition(UUID courseId, UUID parentId) {
		List<ContentNode> siblings = loadSiblings(courseId, parentId);
		if (siblings.isEmpty()) {
			return 0;
		}
		return siblings.get(siblings.size() - 1).getPosition() + 1;
	}

	private List<ContentNode> loadSiblings(UUID courseId, UUID parentId) {
		if (parentId == null) {
			return nodes.findByCourseIdAndParentIdIsNullAndDeletedAtIsNullOrderByPositionAsc(courseId);
		}
		return nodes.findByCourseIdAndParentIdAndDeletedAtIsNullOrderByPositionAsc(courseId, parentId);
	}

	private boolean subtreeHasLecture(UUID courseId, UUID sectionId) {
		for (ContentNode child : loadSiblings(courseId, sectionId)) {
			if ("LECTURE".equals(child.getNodeType())) {
				return true;
			}
			if ("SECTION".equals(child.getNodeType()) && subtreeHasLecture(courseId, child.getId())) {
				return true;
			}
		}
		return false;
	}

	private void deleteEmptySections(UUID courseId, UUID sectionId) {
		for (ContentNode child : loadSiblings(courseId, sectionId)) {
			if ("SECTION".equals(child.getNodeType())) {
				deleteEmptySections(courseId, child.getId());
				child.setDeletedAt(Instant.now());
			}
		}
	}

	private boolean createsCycle(UUID nodeId, UUID newParentId) {
		UUID current = newParentId;
		while (current != null) {
			if (current.equals(nodeId)) {
				return true;
			}
			current = nodes.findById(current).map(ContentNode::getParentId).orElse(null);
		}
		return false;
	}

	private ContentNode validateParent(UUID courseId, UUID parentId, String childType) {
		if (parentId == null) {
			return null;
		}
		ContentNode parent = requireNode(courseId, parentId);
		if ("LECTURE".equals(parent.getNodeType())) {
			throw nodeTypeRejected();
		}
		return parent;
	}

	private static void validateNodeType(String nodeType) {
		if (!NODE_TYPES.contains(nodeType)) {
			throw nodeTypeRejected();
		}
	}

	private Course requireCourse(UUID courseId) {
		return courses.findByIdAndDeletedAtIsNull(courseId).orElseThrow(this::notFound);
	}

	private ContentNode requireNode(UUID courseId, UUID nodeId) {
		return nodes.findByIdAndCourseIdAndDeletedAtIsNull(nodeId, courseId).orElseThrow(this::notFound);
	}

	private static UUID readParentId(JsonNode body) {
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

	private NodeView view(ContentNode node) {
		return new NodeView(node.getId(), node.getParentId(), node.getNodeType(), node.getTitle(), node.getPosition(),
				node.isFree(), node.getVideoProvider(), node.getVideoRef(), node.getNotes());
	}

	private static ApiException nodeTypeRejected() {
		return new ApiException(ErrorCode.NODE_TYPE_REJECTED, HttpStatus.BAD_REQUEST, "Node type or placement is invalid.");
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	public record NodeView(UUID id, UUID parentId, String nodeType, String title, int position, boolean free,
			String videoProvider, String videoRef, String notes) {
	}

}
