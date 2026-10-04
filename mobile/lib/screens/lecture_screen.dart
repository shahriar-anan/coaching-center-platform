import 'package:flutter/material.dart';

import '../api/api_exception.dart';
import '../api/auth_api.dart';
import '../api/catalog_models.dart';
import '../api/error_text.dart';
import '../i18n/messages.dart';
import '../catalog/file_handoff.dart';
import '../files/content_type_label.dart';
import '../widgets/lecture_player.dart';

class LectureScreen extends StatefulWidget {
  const LectureScreen({
    required this.api,
    required this.messages,
    required this.courseId,
    required this.lectureId,
    required this.lectureTitle,
    super.key,
  });

  final AuthApi api;
  final Messages messages;
  final String courseId;
  final String lectureId;
  final String lectureTitle;

  @override
  State<LectureScreen> createState() => _LectureScreenState();
}

class _LectureScreenState extends State<LectureScreen> {
  LectureContent? _content;
  String? _errorCode;
  bool _loading = true;
  final Map<String, String> _materialStatus = {};
  final Set<String> _materialLoading = {};

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final content = await widget.api.getLecture(widget.courseId, widget.lectureId);
      if (!mounted) {
        return;
      }
      setState(() {
        _content = content;
        _loading = false;
      });
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _errorCode = error.code;
        _loading = false;
      });
    }
  }

  Future<void> _openMaterial(LectureMaterialSummary material) async {
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
        widget.messages,
        fileId: material.id,
        file: file,
      );
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _materialLoading.remove(material.id);
        _materialStatus[material.id] = widget.messages.t(errorMessageKey(error.code));
      });
    }
  }

  Future<void> _openLegacyMaterial(String materialId) async {
    setState(() {
      _materialStatus.remove(materialId);
      _materialLoading.add(materialId);
    });
    try {
      final file = await widget.api.downloadFile(materialId);
      if (!mounted) {
        return;
      }
      setState(() => _materialLoading.remove(materialId));
      await openDownloadedFile(
        context,
        widget.messages,
        fileId: materialId,
        file: file,
      );
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _materialLoading.remove(materialId);
        _materialStatus[materialId] = widget.messages.t(errorMessageKey(error.code));
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      key: const Key('lecture-screen'),
      appBar: AppBar(title: Text(widget.lectureTitle)),
      body: _body(),
    );
  }

  Widget _body() {
    if (_loading) {
      return Center(child: Text(widget.messages.t('catalog.detailLoading')));
    }
    if (_errorCode == 'ENROLLMENT_REQUIRED') {
      return Center(
        key: const Key('lecture-locked'),
        child: Text(widget.messages.t('catalog.lectureLocked'), textAlign: TextAlign.center),
      );
    }
    if (_errorCode != null) {
      return Center(child: Text(widget.messages.t(errorMessageKey(_errorCode!)), textAlign: TextAlign.center));
    }
    final content = _content!;
    final videoRef = content.videoRef;
    final materials = content.materials;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        if (videoRef != null && videoRef.isNotEmpty) LecturePlayer(videoRef: videoRef),
        if (content.notes != null && content.notes!.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(top: 16),
            child: Text(content.notes!, key: const Key('lecture-notes')),
          ),
        if (materials.isNotEmpty) ...[
          Padding(
            padding: const EdgeInsets.only(top: 24, bottom: 8),
            child: Text(widget.messages.t('catalog.materials'), style: Theme.of(context).textTheme.titleMedium),
          ),
          for (final material in materials)
            Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                ListTile(
                  key: Key('lecture-material-${material.id}'),
                  leading: _materialLoading.contains(material.id)
                      ? SizedBox(
                          width: 24,
                          height: 24,
                          child: CircularProgressIndicator(
                            key: Key('lecture-file-loading-${material.id}'),
                            strokeWidth: 2,
                          ),
                        )
                      : null,
                  title: Text(material.originalFileName),
                  subtitle: Text(contentTypeLabel(material.contentType)),
                  onTap: _materialLoading.contains(material.id) ? null : () => _openMaterial(material),
                ),
                if (_materialStatus[material.id] != null)
                  Padding(
                    padding: const EdgeInsets.only(left: 16, bottom: 8),
                    child: Text(_materialStatus[material.id]!, key: Key('material-status-${material.id}')),
                  ),
              ],
            ),
        ] else if (content.materialIds.isNotEmpty) ...[
          Padding(
            padding: const EdgeInsets.only(top: 24, bottom: 8),
            child: Text(widget.messages.t('catalog.materials'), style: Theme.of(context).textTheme.titleMedium),
          ),
          for (final materialId in content.materialIds)
            ListTile(
              key: Key('lecture-material-$materialId'),
              leading: _materialLoading.contains(materialId)
                  ? SizedBox(
                      width: 24,
                      height: 24,
                      child: CircularProgressIndicator(
                        key: Key('lecture-file-loading-$materialId'),
                        strokeWidth: 2,
                      ),
                    )
                  : null,
              title: Text(materialId),
              subtitle: Text(widget.messages.t('catalog.openMaterial')),
              onTap: _materialLoading.contains(materialId) ? null : () => _openLegacyMaterial(materialId),
            ),
        ],
      ],
    );
  }
}
