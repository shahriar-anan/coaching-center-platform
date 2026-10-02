import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/api/auth_api.dart';
import 'package:mobile/auth/jwt_role.dart';
import 'package:mobile/auth/session.dart';

import 'support/jwt.dart';
import 'support/memory_token_store.dart';

void main() {
  test('a student session stores both tokens only in the token store', () async {
    final store = MemoryTokenStore();
    final accessToken = unsignedJwt(role: 'STUDENT');
    const refreshToken = 'refresh-body';

    final gate = await completeLogin(
      store,
      TokenPair(accessToken: accessToken, refreshToken: refreshToken, expiresInSeconds: 900),
    );

    expect(roleFromAccessToken(accessToken), 'STUDENT');
    expect(gate, LoginGate.student);
    expect(store.writes, ['accessToken', 'refreshToken']);
    expect(store.values, {'accessToken': accessToken, 'refreshToken': refreshToken});
  });

  test('an admin session is cleared and no token remains', () async {
    for (final role in ['MASTER_ADMIN', 'SYSTEM_ADMIN']) {
      final store = MemoryTokenStore();
      await store.writeAccessToken('previous-access');
      await store.writeRefreshToken('previous-refresh');

      final gate = await completeLogin(
        store,
        TokenPair(accessToken: unsignedJwt(role: role), refreshToken: 'new-refresh', expiresInSeconds: 900),
      );

      expect(gate, LoginGate.studentsOnly);
      expect(store.values, isEmpty);
    }
  });
}
