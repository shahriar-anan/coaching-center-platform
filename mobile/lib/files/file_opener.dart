import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:open_filex/open_filex.dart';
import 'package:path_provider/path_provider.dart';

import '../api/catalog_models.dart';
import 'safe_file_name.dart';

typedef FileOpenHandler = Future<bool> Function(FileDownload file, {required String fileId});

class FileOpener {
  FileOpener._();

  static final FileOpener instance = FileOpener._();

  FileOpenHandler handoffPdf = handoffDownloadedPdf;

  static Future<String> writeDownloadToDisk({required String fileId, required FileDownload file}) async {
    if (kIsWeb) {
      throw UnsupportedError('File handoff is not supported on web.');
    }
    final dir = await getTemporaryDirectory();
    final folder = Directory('${dir.path}/$fileId');
    if (folder.existsSync()) {
      await folder.delete(recursive: true);
    }
    await folder.create(recursive: true);
    final leaf = sanitizeDownloadLeafName(file.fileName);
    final fileName = leaf.isEmpty ? fallbackLeafName(file.contentType) : leaf;
    final path = '${folder.path}/$fileName';
    await File(path).writeAsBytes(file.bytes);
    return path;
  }

  /// Returns true when a viewer accepted the file, false when no app is available.
  static Future<bool> handoffDownloadedPdf(FileDownload file, {required String fileId}) async {
    if (kIsWeb) {
      return false;
    }
    final path = await writeDownloadToDisk(fileId: fileId, file: file);
    final result = await OpenFilex.open(path);
    return result.type == ResultType.done;
  }
}

bool isImageContentType(String contentType) {
  return contentType.toLowerCase().startsWith('image/');
}

bool isPdfContentType(String contentType) {
  return contentType.toLowerCase() == 'application/pdf';
}
