import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'token_store.dart';

class SecureTokenStore implements TokenStore {
  SecureTokenStore({FlutterSecureStorage? storage}) : _storage = storage ?? const FlutterSecureStorage();

  static const _accessKey = 'accessToken';
  static const _refreshKey = 'refreshToken';

  final FlutterSecureStorage _storage;

  @override
  Future<void> writeAccessToken(String value) {
    return _storage.write(key: _accessKey, value: value);
  }

  @override
  Future<void> writeRefreshToken(String value) {
    return _storage.write(key: _refreshKey, value: value);
  }

  @override
  Future<String?> readAccessToken() {
    return _storage.read(key: _accessKey);
  }

  @override
  Future<String?> readRefreshToken() {
    return _storage.read(key: _refreshKey);
  }

  @override
  Future<void> clear() async {
    await _storage.delete(key: _accessKey);
    await _storage.delete(key: _refreshKey);
  }
}
