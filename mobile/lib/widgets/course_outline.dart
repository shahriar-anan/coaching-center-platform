import 'package:flutter/material.dart';

import '../api/catalog_models.dart';
import '../catalog/catalog_access.dart';

class CourseOutline extends StatelessWidget {
  const CourseOutline({
    required this.nodes,
    required this.accessType,
    required this.enrolled,
    required this.enrollmentKnown,
    required this.onLectureTap,
    super.key,
  });

  final List<CatalogNode> nodes;
  final String accessType;
  final bool enrolled;
  final bool enrollmentKnown;
  final void Function(CatalogNode lecture) onLectureTap;

  @override
  Widget build(BuildContext context) {
    return Column(
      key: const Key('course-tree'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: buildOutlineChildren(
        nodes: nodes,
        parentId: null,
        depth: 0,
        accessType: accessType,
        enrolled: enrolled,
        enrollmentKnown: enrollmentKnown,
        onLectureTap: onLectureTap,
      ),
    );
  }
}

List<Widget> buildOutlineChildren({
  required List<CatalogNode> nodes,
  required String? parentId,
  required int depth,
  required String accessType,
  required bool enrolled,
  required bool enrollmentKnown,
  required void Function(CatalogNode lecture) onLectureTap,
}) {
  final siblings = sortedSiblings(nodes, parentId);
  final widgets = <Widget>[];
  for (final node in siblings) {
    if (node.nodeType == 'SECTION') {
      widgets.add(_sectionBlock(
        section: node,
        nodes: nodes,
        depth: depth,
        accessType: accessType,
        enrolled: enrolled,
        enrollmentKnown: enrollmentKnown,
        onLectureTap: onLectureTap,
      ));
    } else if (node.nodeType == 'LECTURE') {
      widgets.add(_lectureTile(
        lecture: node,
        accessType: accessType,
        enrolled: enrolled,
        enrollmentKnown: enrollmentKnown,
        onLectureTap: onLectureTap,
      ));
    }
  }
  return widgets;
}

Widget _sectionBlock({
  required CatalogNode section,
  required List<CatalogNode> nodes,
  required int depth,
  required String accessType,
  required bool enrolled,
  required bool enrollmentKnown,
  required void Function(CatalogNode lecture) onLectureTap,
}) {
  final children = buildOutlineChildren(
    nodes: nodes,
    parentId: section.id,
    depth: depth + 1,
    accessType: accessType,
    enrolled: enrolled,
    enrollmentKnown: enrollmentKnown,
    onLectureTap: onLectureTap,
  );
  final title = Text(section.title, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600));
  final body = Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      title,
      const SizedBox(height: 8),
      ...children,
    ],
  );

  if (depth == 0) {
    return Card(
      key: Key('course-tree-section-${section.id}'),
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(padding: const EdgeInsets.all(12), child: body),
    );
  }
  return Padding(
    key: Key('course-tree-nested-section-${section.id}'),
    padding: const EdgeInsets.only(left: 12, top: 8),
    child: body,
  );
}

List<CatalogNode> sortedSiblings(List<CatalogNode> nodes, String? parentId) {
  final matches = nodes.where((node) => node.parentId == parentId).toList();
  matches.sort((a, b) {
    final byPosition = a.position.compareTo(b.position);
    if (byPosition != 0) {
      return byPosition;
    }
    return a.id.compareTo(b.id);
  });
  return matches;
}

Widget _lectureTile({
  required CatalogNode lecture,
  required String accessType,
  required bool enrolled,
  required bool enrollmentKnown,
  required void Function(CatalogNode lecture) onLectureTap,
}) {
  final open = CatalogAccess.lectureAppearsOpen(
    accessType: accessType,
    lectureFree: lecture.free,
    enrolled: enrolled,
    enrollmentKnown: enrollmentKnown,
  );
  return ListTile(
    key: Key('course-tree-lecture-${lecture.id}'),
    leading: Icon(open ? Icons.play_circle_outline : Icons.lock_outline),
    title: Text(lecture.title),
    onTap: () => onLectureTap(lecture),
  );
}
