import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../api/catalog_models.dart';

/// In-app preview for images only. PDFs use [openDownloadedFile] handoff.
class FilePreviewScreen extends StatelessWidget {
  const FilePreviewScreen({required this.file, super.key});

  final FileDownload file;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(file.fileName)),
      body: Center(
        child: Image.memory(
          Uint8List.fromList(file.bytes),
          key: const Key('file-image-viewer'),
          fit: BoxFit.contain,
        ),
      ),
    );
  }
}
