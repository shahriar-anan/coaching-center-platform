import 'package:flutter/material.dart';

import '../api/catalog_models.dart';
import '../files/file_opener.dart';
import '../i18n/messages.dart';
import '../screens/file_preview_screen.dart';

Future<void> openDownloadedFile(
  BuildContext context,
  Messages messages, {
  required String fileId,
  required FileDownload file,
}) async {
  if (isImageContentType(file.contentType)) {
    await Navigator.of(context).push(
      MaterialPageRoute<void>(builder: (context) => FilePreviewScreen(file: file)),
    );
    return;
  }
  if (isPdfContentType(file.contentType)) {
    final opened = await FileOpener.instance.handoffPdf(file, fileId: fileId);
    if (!context.mounted) {
      return;
    }
    if (!opened) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(key: const Key('pdf-handoff-failed'), content: Text(messages.t('catalog.pdfOpenFailed'))),
      );
    }
    return;
  }
  final opened = await FileOpener.instance.handoffPdf(file, fileId: fileId);
  if (!context.mounted) {
    return;
  }
  if (!opened) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(messages.t('catalog.pdfOpenFailed'))),
    );
  }
}
