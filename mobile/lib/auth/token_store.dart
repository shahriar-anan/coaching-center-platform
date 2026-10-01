/// Access and refresh tokens. Implementations must not log either value.
abstract class TokenStore {
  Future<void> writeAccessToken(String value);

  Future<void> writeRefreshToken(String value);

  Future<String?> readAccessToken();

  Future<String?> readRefreshToken();

  Future<void> clear();
}
