import 'package:flutter/material.dart';

import '../api/api_exception.dart';
import '../api/auth_api.dart';
import '../api/catalog_models.dart';
import '../api/error_text.dart';
import '../catalog/catalog_access.dart';
import '../catalog/file_handoff.dart';
import '../i18n/locale_controller.dart';
import '../i18n/messages.dart';
import '../files/content_type_label.dart';
import '../widgets/catalog_cover.dart';
import '../widgets/course_outline.dart';
import 'catalog_browse_tab.dart';
import 'course_purchase_screen.dart';
import 'lecture_screen.dart';

class CourseDetailScreen extends StatefulWidget {
  const CourseDetailScreen({required this.api, required this.locale, required this.courseId, super.key});

  final AuthApi api;
  final LocaleController locale;
  final String courseId;

  @override
  State<CourseDetailScreen> createState() => _CourseDetailScreenState();
}

class _CourseDetailScreenState extends State<CourseDetailScreen> {
  CourseDetail? _course;
  String? _error;
  bool _loading = true;
  late bool _placementEnglish;
  bool _enrolled = false;
  bool _enrollmentKnown = false;
  final Map<String, String> _materialStatus = {};
  final Set<String> _materialLoading = {};

  @override
  void initState() {
    super.initState();
    _placementEnglish = widget.locale.code != 'bn';
    _load();
  }

  @override
  void didUpdateWidget(CourseDetailScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.locale.code != widget.locale.code) {
      _placementEnglish = widget.locale.code != 'bn';
    }
  }

  Future<void> _load() async {
    try {
      final course = await widget.api.getCourse(widget.courseId);
      var enrolled = false;
      var enrollmentKnown = false;
      try {
        final myCourses = await widget.api.listMyCourses();
        enrollmentKnown = true;
        enrolled = myCourses.any((entry) => entry.courseId == course.id);
      } on ApiException {
        enrollmentKnown = false;
      }
      if (!mounted) {
        return;
      }
      setState(() {
        _course = course;
        _enrolled = enrolled;
        _enrollmentKnown = enrollmentKnown;
        _loading = false;
      });
    } on ApiException {
      if (!mounted) {
        return;
      }
      setState(() {
        _error = widget.locale.messages.t('catalog.loadFailed');
        _loading = false;
      });
    }
  }

  void _openPurchase() {
    final course = _course!;
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (context) => CoursePurchaseScreen(api: widget.api, messages: widget.locale.messages, course: course),
      ),
    );
  }

  void _openLecture(CatalogNode node) {
    final course = _course!;
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (context) => LectureScreen(
          api: widget.api,
          messages: widget.locale.messages,
          courseId: course.id,
          lectureId: node.id,
          lectureTitle: node.title,
        ),
      ),
    );
  }

  bool _materialAppearsOpen(CourseMaterialSummary material) {
    final course = _course!;
    return CatalogAccess.courseMaterialAppearsOpen(
      accessType: course.accessType,
      materialFree: material.free,
      enrolled: _enrolled,
      enrollmentKnown: _enrollmentKnown,
    );
  }

  Future<void> _openCourseMaterial(CourseMaterialSummary material) async {
    setState(() {
      _materialStatus.remove(material.id);
      _materialLoading.add(material.id);
    });
    try {
      final file = await widget.api.downloadFile(material.id);
      if (!mounted) {
        return;
      }
      setState(() => _materialLoading.remove(material.id));
      await openDownloadedFile(
        context,
        widget.locale.messages,
        fileId: material.id,
        file: file,
      );
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _materialLoading.remove(material.id);
        _materialStatus[material.id] = widget.locale.messages.t(errorMessageKey(error.code));
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final messages = widget.locale.messages;
    final localeCode = widget.locale.code;
    return Scaffold(
      key: const Key('course-detail'),
      appBar: AppBar(
        title: Text(_course == null ? messages.t('catalog.tabCourses') : catalogTitle(localeCode, _course!.titleEn, _course!.titleBn)),
      ),
      body: _loading
          ? Center(child: Text(messages.t('catalog.detailLoading')))
          : _error != null
          ? Center(child: Text(_error!, textAlign: TextAlign.center))
          : _buildDetail(localeCode),
    );
  }

  Widget _buildDetail(String localeCode) {
    final messages = widget.locale.messages;
    final course = _course!;
    final hasCover = CatalogCover.shouldShow(course.coverFileId);
    return ListView(
      key: const Key('course-detail-body'),
      padding: const EdgeInsets.all(16),
      children: [
        if (hasCover) ...[
          CatalogCover(
            api: widget.api,
            messages: messages,
            coverFileId: course.coverFileId!,
            height: 200,
            imageKey: const Key('course-cover'),
            loadingKey: const Key('course-cover-loading'),
          ),
          const SizedBox(height: 16),
        ],
        if (course.categoryPath.isNotEmpty) _buildPlacement(messages),
        Text(catalogSubtitle(localeCode, course.titleEn, course.titleBn), style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 8),
        Text(course.description),
        const SizedBox(height: 8),
        Text('${messages.t('catalog.deliveryMode')}: ${course.deliveryMode}'),
        if (course.price != null) Text('${messages.t('catalog.price')}: ${course.price}'),
        if (course.accessType != 'FULL_FREE' && course.price != null) ...[
          const SizedBox(height: 16),
          FilledButton(
            key: const Key('course-purchase'),
            onPressed: _openPurchase,
            child: Text(messages.t('catalog.purchaseCourse')),
          ),
        ],
        const SizedBox(height: 16),
        Text(messages.t('catalog.contentTree'), style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 8),
        CourseOutline(
          nodes: course.contentTree,
          accessType: course.accessType,
          enrolled: _enrolled,
          enrollmentKnown: _enrollmentKnown,
          onLectureTap: _openLecture,
        ),
        if (course.courseMaterials.isNotEmpty) ...[
          const SizedBox(height: 16),
          Text(messages.t('catalog.courseFiles'), style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          for (final material in course.courseMaterials) _courseMaterialTile(material),
        ],
      ],
    );
  }

  Widget _buildPlacement(Messages messages) {
    final course = _course!;
    final pathText = course.categoryPath
        .map((item) => _placementEnglish ? item.titleEn : item.titleBn)
        .join(' › ');
    return Padding(
      key: const Key('course-placement'),
      padding: const EdgeInsets.only(bottom: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(messages.t('catalog.placedIn'), style: Theme.of(context).textTheme.titleSmall),
          const SizedBox(height: 8),
          SegmentedButton<bool>(
            key: const Key('course-placement-language'),
            segments: [
              ButtonSegment(value: true, label: Text(messages.t('locale.en'))),
              ButtonSegment(value: false, label: Text(messages.t('locale.bn'))),
            ],
            selected: {_placementEnglish},
            onSelectionChanged: (selection) {
              setState(() => _placementEnglish = selection.first);
            },
          ),
          const SizedBox(height: 8),
          Text(pathText, key: const Key('course-placement-path')),
        ],
      ),
    );
  }

  Widget _courseMaterialTile(CourseMaterialSummary material) {
    final allowed = _materialAppearsOpen(material);
    final status = _materialStatus[material.id];
    final loading = _materialLoading.contains(material.id);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        ListTile(
          key: Key('course-file-${material.id}'),
          leading: loading
              ? SizedBox(
                  width: 24,
                  height: 24,
                  child: CircularProgressIndicator(key: Key('course-file-loading-${material.id}'), strokeWidth: 2),
                )
              : Icon(allowed ? Icons.insert_drive_file_outlined : Icons.lock_outline),
          title: Text(material.originalFileName),
          subtitle: Text(contentTypeLabel(material.contentType)),
          onTap: loading ? null : () => _openCourseMaterial(material),
        ),
        if (status != null)
          Padding(
            padding: const EdgeInsets.only(left: 16, bottom: 8),
            child: Text(status, key: Key('course-file-status-${material.id}')),
          ),
      ],
    );
  }
}
