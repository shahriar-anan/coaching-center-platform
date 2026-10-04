import 'dart:typed_data';

/// In-memory cover bytes for on-screen decode only. Not persisted; failures are not stored.
class CoverByteCache {
  CoverByteCache._();

  static final CoverByteCache instance = CoverByteCache._();

  static const int maxDisplayBytes = 2 * 1024 * 1024;

  final Map<String, Uint8List> _accepted = {};

  Uint8List? get(String fileId) => _accepted[fileId];

  void putAccepted(String fileId, Uint8List bytes) {
    _accepted[fileId] = bytes;
  }

  void clearForTesting() {
    _accepted.clear();
  }
}
