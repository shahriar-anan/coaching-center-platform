import '../api/auth_api.dart';
import 'jwt_role.dart';
import 'token_store.dart';

enum LoginGate { student, studentsOnly }

/// Stores tokens only for a student. Any other role is cleared and rejected.
Future<LoginGate> completeLogin(TokenStore store, TokenPair tokens) async {
  if (roleFromAccessToken(tokens.accessToken) != 'STUDENT') {
    await store.clear();
    return LoginGate.studentsOnly;
  }
  await store.writeAccessToken(tokens.accessToken);
  await store.writeRefreshToken(tokens.refreshToken);
  return LoginGate.student;
}
