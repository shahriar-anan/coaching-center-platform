import 'package:mobile/auth/token_store.dart';

/// In-memory stand-in for secure storage. Tokens must not be written anywhere else.
class MemoryTokenStore implements TokenStore {
  final Map<String, String> values = {};
  final List<String> writes = [];

  @override
  Future<void> writeAccessToken(String value) async {
    writes.add('accessToken');
    values['accessToken'] = value;
  }

  @override
  Future<void> writeRefreshToken(String value) async {
    writes.add('refreshToken');
    values['refreshToken'] = value;
  }

  @override
  Future<String?> readAccessToken() async => values['accessToken'];

  @override
  Future<String?> readRefreshToken() async => values['refreshToken'];

  @override
  Future<void> clear() async {
    values.clear();
  }
}
