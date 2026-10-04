import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../api/api_exception.dart';
import '../api/auth_api.dart';
import '../files/cover_byte_cache.dart';
import '../i18n/messages.dart';

enum _CoverState { loading, ready, failed, tooLarge }

class CatalogCover extends StatefulWidget {
  const CatalogCover({
    required this.api,
    required this.messages,
    required this.coverFileId,
    this.width,
    this.height = 120,
    this.imageKey = const Key('catalog-cover'),
    this.loadingKey,
    super.key,
  });

  final AuthApi api;
  final Messages messages;
  final String coverFileId;
  final double? width;
  final double height;
  final Key imageKey;
  final Key? loadingKey;

  static bool shouldShow(String? coverFileId) {
    return coverFileId != null && coverFileId.isNotEmpty;
  }

  @override
  State<CatalogCover> createState() => _CatalogCoverState();
}

class _CatalogCoverState extends State<CatalogCover> {
  _CoverState _state = _CoverState.loading;
  Uint8List? _bytes;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void didUpdateWidget(CatalogCover oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.coverFileId != widget.coverFileId) {
      _bytes = null;
      _state = _CoverState.loading;
      _load();
    }
  }

  Future<void> _load() async {
    final coverFileId = widget.coverFileId;
    final cached = CoverByteCache.instance.get(coverFileId);
    if (cached != null) {
      if (!mounted) {
        return;
      }
      setState(() {
        _bytes = cached;
        _state = _CoverState.ready;
      });
      return;
    }
    try {
      final file = await widget.api.downloadFile(coverFileId);
      if (!mounted) {
        return;
      }
      if (file.bytes.length > CoverByteCache.maxDisplayBytes) {
        setState(() => _state = _CoverState.tooLarge);
        return;
      }
      final bytes = Uint8List.fromList(file.bytes);
      CoverByteCache.instance.putAccepted(coverFileId, bytes);
      setState(() {
        _bytes = bytes;
        _state = _CoverState.ready;
      });
    } on ApiException {
      if (!mounted) {
        return;
      }
      setState(() => _state = _CoverState.failed);
    }
  }

  @override
  Widget build(BuildContext context) {
    final width = widget.width;
    final height = widget.height;
    switch (_state) {
      case _CoverState.loading:
        return SizedBox(
          width: width,
          height: height,
          child: Center(
            child: CircularProgressIndicator(
              key: widget.loadingKey ?? const Key('course-cover-loading'),
            ),
          ),
        );
      case _CoverState.failed:
        return SizedBox(
          width: width,
          height: height,
          child: Center(
            key: const Key('course-cover-failed'),
            child: Text(
              widget.messages.t('catalog.coverFailed'),
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ),
        );
      case _CoverState.tooLarge:
        return SizedBox(
          width: width,
          height: height,
          child: Center(
            key: const Key('course-cover-too-large'),
            child: Text(
              widget.messages.t('catalog.coverTooLarge'),
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ),
        );
      case _CoverState.ready:
        return Image.memory(
          _bytes!,
          key: widget.imageKey,
          width: width,
          height: height,
          fit: BoxFit.contain,
        );
    }
  }
}
