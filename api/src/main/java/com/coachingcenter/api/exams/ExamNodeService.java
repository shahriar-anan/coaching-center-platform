package com.coachingcenter.api.exams;

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
public class ExamNodeService {

	private static final Set<String> NODE_TYPES = Set.of("SECTION", "PAPER");

	private final ExamModuleRepository modules;

	private final ExamNodeRepository nodes;

	private final ExamComponentService components;

	public ExamNodeService(ExamModuleRepository modules, ExamNodeRepository nodes, ExamComponentService components) {
		this.modules = modules;
		this.nodes = nodes;
		this.components = components;
	}

	@Transactional
	public NodeView create(UUID moduleId, JsonNode body) {
		requireModule(moduleId);
		String nodeType = requiredText(body, "nodeType");
		validateNodeType(nodeType);
		UUID parentId = readParentId(body);
		validateParent(moduleId, parentId);
		if (parentId != null) {
			ExamNode parent = requireNode(moduleId, parentId);
			if ("PAPER".equals(parent.getNodeType())) {
				throw nodeTypeRejected();
			}
		}
		int position = nextPosition(moduleId, parentId);
		if (body.has("position") && !body.get("position").isNull()) {
			position = body.get("position").asInt();
		}
		ExamNode node = new ExamNode();
		node.setModuleId(moduleId);
		node.setParentId(parentId);
		node.setNodeType(nodeType);
		node.setTitleEn(requiredText(body, "titleEn"));
		node.setTitleBn(requiredText(body, "titleBn"));
		node.setPosition(position);
		return view(nodes.save(node));
	}

	@Transactional
	public NodeView update(UUID moduleId, UUID nodeId, JsonNode body) {
		requireModule(moduleId);
		ExamNode node = requireNode(moduleId, nodeId);
		if (body.has("titleEn") && !body.get("titleEn").isNull()) {
			node.setTitleEn(requiredText(body, "titleEn"));
		}
		if (body.has("titleBn") && !body.get("titleBn").isNull()) {
			node.setTitleBn(requiredText(body, "titleBn"));
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
			validateParent(moduleId, newParentId);
			if (newParentId != null) {
				ExamNode newParent = requireNode(moduleId, newParentId);
				if ("PAPER".equals(newParent.getNodeType())) {
					throw nodeTypeRejected();
				}
				if (createsCycle(nodeId, newParentId)) {
					throw nodeTypeRejected();
				}
			}
			node.setParentId(newParentId);
			node.setPosition(nextPosition(moduleId, newParentId));
		}
		return view(node);
	}

	@Transactional
	public void reorder(UUID moduleId, JsonNode body) {
		requireModule(moduleId);
		UUID parentId = readParentId(body);
		JsonNode ids = body.get("nodeIds");
		if (ids == null || !ids.isArray() || ids.isEmpty()) {
			throw validationFailed();
		}
		List<UUID> ordered = new ArrayList<>();
		for (JsonNode idNode : ids) {
			ordered.add(UUID.fromString(idNode.asText()));
		}
		List<ExamNode> siblings = loadSiblings(moduleId, parentId);
		if (siblings.size() != ordered.size()) {
			throw validationFailed();
		}
		Set<UUID> siblingIds = new HashSet<>();
		for (ExamNode sibling : siblings) {
			siblingIds.add(sibling.getId());
		}
		if (!siblingIds.equals(new HashSet<>(ordered))) {
			throw validationFailed();
		}
		Map<UUID, ExamNode> byId = new HashMap<>();
		for (ExamNode sibling : siblings) {
			byId.put(sibling.getId(), sibling);
		}
		for (int index = 0; index < ordered.size(); index++) {
			ExamNode sibling = byId.get(ordered.get(index));
			sibling.setPosition(index);
		}
	}

	@Transactional
	public void delete(UUID moduleId, UUID nodeId) {
		requireModule(moduleId);
		ExamNode node = requireNode(moduleId, nodeId);
		if ("SECTION".equals(node.getNodeType())) {
			if (subtreeHasPaper(moduleId, nodeId)) {
				throw new ApiException(ErrorCode.SECTION_NOT_EMPTY, HttpStatus.CONFLICT, "Section still has a paper.");
			}
			deleteEmptySections(moduleId, nodeId);
		}
		else {
			components.softDeleteForPaper(nodeId);
		}
		node.setDeletedAt(Instant.now());
	}

	@Transactional(readOnly = true)
	public List<NodeView> list(UUID moduleId) {
		requireModule(moduleId);
		return nodes.findByModuleIdAndDeletedAtIsNullOrderByPositionAsc(moduleId).stream().map(this::view).toList();
	}

	private boolean subtreeHasPaper(UUID moduleId, UUID sectionId) {
		for (ExamNode child : loadSiblings(moduleId, sectionId)) {
			if ("PAPER".equals(child.getNodeType())) {
				return true;
			}
			if ("SECTION".equals(child.getNodeType()) && subtreeHasPaper(moduleId, child.getId())) {
				return true;
			}
		}
		return false;
	}

	private void deleteEmptySections(UUID moduleId, UUID sectionId) {
		for (ExamNode child : loadSiblings(moduleId, sectionId)) {
			if ("SECTION".equals(child.getNodeType())) {
				deleteEmptySections(moduleId, child.getId());
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
			current = nodes.findById(current).map(ExamNode::getParentId).orElse(null);
		}
		return false;
	}

	private void validateParent(UUID moduleId, UUID parentId) {
		if (parentId == null) {
			return;
		}
		ExamNode parent = requireNode(moduleId, parentId);
		if ("PAPER".equals(parent.getNodeType())) {
			throw nodeTypeRejected();
		}
	}

	private int nextPosition(UUID moduleId, UUID parentId) {
		List<ExamNode> siblings = loadSiblings(moduleId, parentId);
		if (siblings.isEmpty()) {
			return 0;
		}
		return siblings.get(siblings.size() - 1).getPosition() + 1;
	}

	private List<ExamNode> loadSiblings(UUID moduleId, UUID parentId) {
		if (parentId == null) {
			return nodes.findByModuleIdAndParentIdIsNullAndDeletedAtIsNullOrderByPositionAsc(moduleId);
		}
		return nodes.findByModuleIdAndParentIdAndDeletedAtIsNullOrderByPositionAsc(moduleId, parentId);
	}

	private static void validateNodeType(String nodeType) {
		if (!NODE_TYPES.contains(nodeType)) {
			throw nodeTypeRejected();
		}
	}

	private ExamModule requireModule(UUID moduleId) {
		return modules.findByIdAndDeletedAtIsNull(moduleId).orElseThrow(() -> notFound());
	}

	private ExamNode requireNode(UUID moduleId, UUID nodeId) {
		return nodes.findByIdAndModuleIdAndDeletedAtIsNull(nodeId, moduleId).orElseThrow(() -> notFound());
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

	private NodeView view(ExamNode node) {
		return new NodeView(node.getId(), node.getParentId(), node.getNodeType(), node.getTitleEn(), node.getTitleBn(),
				node.getPosition());
	}

	private static ApiException nodeTypeRejected() {
		return new ApiException(ErrorCode.NODE_TYPE_REJECTED, HttpStatus.BAD_REQUEST, "Node type or placement is invalid.");
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private static ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	public record NodeView(UUID id, UUID parentId, String nodeType, String titleEn, String titleBn, int position) {
	}

}
