import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:mobile/api/auth_api.dart';
import 'package:mobile/app.dart';
import 'package:mobile/i18n/en.dart';

import 'support/jwt.dart';
import 'support/memory_token_store.dart';

void main() {
  const baseUrl = 'http://10.0.2.2:8080';

  http.Response tokens(String accessToken, String refreshToken) {
    return http.Response(
      jsonEncode({'accessToken': accessToken, 'expiresInSeconds': 900, 'refreshToken': refreshToken}),
      200,
      headers: {'content-type': 'application/json'},
    );
  }

  http.Response me(String fullName) {
    return http.Response(
      jsonEncode({
        'id': 'user-1',
        'phone': '+8801700000002',
        'email': 'student@example.com',
        'emailVerifiedAt': null,
        'role': 'STUDENT',
        'status': 'ACTIVE',
        'fullName': fullName,
        'studentCode': 'S-1',
      }),
      200,
      headers: {'content-type': 'application/json'},
    );
  }

  http.Response error(int status, String code) {
    return http.Response(
      jsonEncode({'code': code, 'message': 'nope', 'timestamp': '2026-09-30T00:00:00Z', 'requestId': 'req-1'}),
      status,
      headers: {'content-type': 'application/json'},
    );
  }

  Future<List<http.Request>> pumpApp(
    WidgetTester tester, {
    required MemoryTokenStore store,
    required http.Response Function(http.Request request) handler,
  }) async {
    final requests = <http.Request>[];
    final api = AuthApi(
      baseUrl: baseUrl,
      client: MockClient((request) async {
        requests.add(request);
        return handler(request);
      }),
    );
    await tester.pumpWidget(CoachingApp(key: UniqueKey(), api: api, tokens: store));
    await tester.pumpAndSettle();
    return requests;
  }

  testWidgets('a stored refresh token refreshes on launch and opens the home', (tester) async {
    final store = MemoryTokenStore();
    final previousAccess = unsignedJwt(role: 'STUDENT');
    final nextAccess = unsignedJwt(role: 'STUDENT');
    await store.writeAccessToken(previousAccess);
    await store.writeRefreshToken('old-refresh');

    final requests = await pumpApp(
      tester,
      store: store,
      handler: (request) {
        if (request.url.path.endsWith('/auth/refresh')) {
          return tokens(nextAccess, 'new-refresh');
        }
        if (request.url.path.endsWith('/me')) {
          return me('Student One');
        }
        return error(500, 'INTERNAL_ERROR');
      },
    );

    final refresh = requests.singleWhere((request) => request.url.path.endsWith('/auth/refresh'));
    expect(refresh.method, 'POST');
    expect(refresh.headers['x-client-type'], 'mobile');
    expect(refresh.headers['cookie'], isNull);
    expect(jsonDecode(refresh.body), {'refreshToken': 'old-refresh'});
    expect(find.text('Student One'), findsOneWidget);
    expect(find.byKey(const Key('home')), findsOneWidget);
    expect(store.values['accessToken'], nextAccess);
    expect(store.values['refreshToken'], 'new-refresh');
  });

  testWidgets('a failed refresh clears both tokens and opens login', (tester) async {
    final store = MemoryTokenStore();
    await store.writeAccessToken(unsignedJwt(role: 'STUDENT'));
    await store.writeRefreshToken('old-refresh');

    await pumpApp(tester, store: store, handler: (request) => error(401, 'INVALID_REFRESH_TOKEN'));

    expect(find.byKey(const Key('home')), findsNothing);
    expect(find.widgetWithText(AppBar, english.t('auth.loginTitle')), findsOneWidget);
    expect(store.values, isEmpty);
  });

  testWidgets('the first 401 refreshes once and a second 401 returns to login', (tester) async {
    final store = MemoryTokenStore();
    final firstAccess = unsignedJwt(role: 'STUDENT');
    final refreshedAccess = unsignedJwt(role: 'STUDENT');
    var meCalls = 0;
    final requests = await pumpApp(
      tester,
      store: store,
      handler: (request) {
        if (request.url.path.endsWith('/auth/login')) {
          return tokens(firstAccess, 'refresh-1');
        }
        if (request.url.path.endsWith('/auth/refresh')) {
          return tokens(refreshedAccess, 'refresh-2');
        }
        if (request.url.path.endsWith('/me')) {
          meCalls++;
          if (meCalls == 1) {
            return error(401, 'UNAUTHENTICATED');
          }
          return me('Student One');
        }
        return error(500, 'INTERNAL_ERROR');
      },
    );

    await tester.enterText(find.byKey(const Key('identifier')), 'student@example.com');
    await tester.enterText(find.byKey(const Key('password')), 'password1');
    await tester.tap(find.byKey(const Key('login-submit')));
    await tester.pumpAndSettle();

    expect(meCalls, 2);
    expect(requests.where((request) => request.url.path.endsWith('/auth/refresh')).length, 1);
    expect(find.text('Student One'), findsOneWidget);
    expect(store.values['accessToken'], refreshedAccess);
    expect(store.values['refreshToken'], 'refresh-2');

    meCalls = 0;
    final secondStore = MemoryTokenStore();
    await pumpApp(
      tester,
      store: secondStore,
      handler: (request) {
        if (request.url.path.endsWith('/auth/login')) {
          return tokens(firstAccess, 'refresh-1');
        }
        if (request.url.path.endsWith('/auth/refresh')) {
          return tokens(refreshedAccess, 'refresh-2');
        }
        if (request.url.path.endsWith('/me')) {
          meCalls++;
          return error(401, 'UNAUTHENTICATED');
        }
        return error(500, 'INTERNAL_ERROR');
      },
    );
    await tester.enterText(find.byKey(const Key('identifier')), 'student@example.com');
    await tester.enterText(find.byKey(const Key('password')), 'password1');
    await tester.tap(find.byKey(const Key('login-submit')));
    await tester.pumpAndSettle();

    expect(meCalls, 2);
    expect(find.byKey(const Key('home')), findsNothing);
    expect(find.widgetWithText(AppBar, english.t('auth.loginTitle')), findsOneWidget);
    expect(secondStore.values, isEmpty);
  });

  testWidgets('logout sends the refresh token with the bearer and clears storage', (tester) async {
    final store = MemoryTokenStore();
    final nextAccess = unsignedJwt(role: 'STUDENT');
    await store.writeAccessToken(unsignedJwt(role: 'STUDENT'));
    await store.writeRefreshToken('old-refresh');
    final requests = await pumpApp(
      tester,
      store: store,
      handler: (request) {
        if (request.url.path.endsWith('/auth/refresh')) {
          return tokens(nextAccess, 'refresh-2');
        }
        if (request.url.path.endsWith('/auth/logout')) {
          return http.Response('', 204);
        }
        if (request.url.path.endsWith('/me')) {
          return me('Student One');
        }
        return error(500, 'INTERNAL_ERROR');
      },
    );

    expect(find.text('Student One'), findsOneWidget);
    await tester.tap(find.byKey(const Key('logout')));
    await tester.pumpAndSettle();

    final logout = requests.singleWhere((request) => request.url.path.endsWith('/auth/logout'));
    expect(logout.method, 'POST');
    expect(logout.headers['x-client-type'], 'mobile');
    expect(logout.headers['authorization'], 'Bearer $nextAccess');
    expect(logout.headers['cookie'], isNull);
    expect(jsonDecode(logout.body), {'refreshToken': 'refresh-2'});
    expect(store.values, isEmpty);
    expect(find.byKey(const Key('home')), findsNothing);
    expect(find.widgetWithText(AppBar, english.t('auth.loginTitle')), findsOneWidget);
  });

  testWidgets('the home is absent when signed out and a new launch restores a valid session', (tester) async {
    final store = MemoryTokenStore();
    await pumpApp(tester, store: store, handler: (request) => error(500, 'INTERNAL_ERROR'));
    expect(find.byKey(const Key('home')), findsNothing);
    expect(find.widgetWithText(AppBar, english.t('auth.loginTitle')), findsOneWidget);

    final restored = MemoryTokenStore();
    final nextAccess = unsignedJwt(role: 'STUDENT');
    await restored.writeAccessToken(unsignedJwt(role: 'STUDENT'));
    await restored.writeRefreshToken('kept-refresh');
    await pumpApp(
      tester,
      store: restored,
      handler: (request) {
        if (request.url.path.endsWith('/auth/refresh')) {
          return tokens(nextAccess, 'kept-refresh-2');
        }
        if (request.url.path.endsWith('/me')) {
          return me('Returned Student');
        }
        return error(500, 'INTERNAL_ERROR');
      },
    );

    expect(find.text('Returned Student'), findsOneWidget);
    expect(find.byKey(const Key('home')), findsOneWidget);
    expect(restored.values['refreshToken'], 'kept-refresh-2');
  });
}
