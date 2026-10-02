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

  Future<http.Request> pumpLogin(
    WidgetTester tester, {
    required MemoryTokenStore store,
    required String identifier,
    required String password,
    required http.Response response,
    String homeName = 'Student One',
  }) async {
    late http.Request captured;
    final api = AuthApi(
      baseUrl: baseUrl,
      client: MockClient((request) async {
        if (request.url.path.endsWith('/me')) {
          return http.Response(
            jsonEncode({
              'id': 'user-1',
              'phone': '+8801700000002',
              'email': 'student@example.com',
              'emailVerifiedAt': null,
              'role': 'STUDENT',
              'status': 'ACTIVE',
              'fullName': homeName,
              'studentCode': 'S-1',
            }),
            200,
            headers: {'content-type': 'application/json'},
          );
        }
        captured = request;
        return response;
      }),
    );
    await tester.pumpWidget(CoachingApp(key: UniqueKey(), api: api, tokens: store));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('identifier')), identifier);
    await tester.enterText(find.byKey(const Key('password')), password);
    await tester.tap(find.byKey(const Key('login-submit')));
    await tester.pumpAndSettle();
    return captured;
  }

  http.Response tokenResponse(String role, {String? refreshToken, Map<String, String>? headers}) {
    return http.Response(
      jsonEncode({
        'accessToken': unsignedJwt(role: role),
        'expiresInSeconds': 900,
        'refreshToken': ?refreshToken,
      }),
      200,
      headers: {'content-type': 'application/json', ...?headers},
    );
  }

  testWidgets('login sends the mobile client header and JSON body without a cookie', (tester) async {
    final store = MemoryTokenStore();
    final captured = await pumpLogin(
      tester,
      store: store,
      identifier: '01700000002',
      password: 'password1',
      response: tokenResponse('STUDENT', refreshToken: 'refresh-body'),
    );

    expect(captured.method, 'POST');
    expect(captured.url.path, '/api/v1/auth/login');
    expect(captured.headers['x-client-type'], 'mobile');
    expect(captured.headers['cookie'], isNull);
    expect(jsonDecode(captured.body), {'identifier': '01700000002', 'password': 'password1'});
    expect(captured.body.contains('refresh'), isFalse);
  });

  testWidgets('a student success stores both tokens from the JSON body', (tester) async {
    final store = MemoryTokenStore();
    final accessToken = unsignedJwt(role: 'STUDENT');
    await pumpLogin(
      tester,
      store: store,
      identifier: 'student@example.com',
      password: 'password1',
      response: http.Response(
        jsonEncode({'accessToken': accessToken, 'expiresInSeconds': 900, 'refreshToken': 'refresh-body'}),
        200,
        headers: {'content-type': 'application/json'},
      ),
    );

    expect(find.text('Student One'), findsOneWidget);
    expect(find.byKey(const Key('home')), findsOneWidget);
    expect(store.writes, ['accessToken', 'refreshToken']);
    expect(store.values, {'accessToken': accessToken, 'refreshToken': 'refresh-body'});
  });

  testWidgets('invalid credentials render the generic translated login error', (tester) async {
    final store = MemoryTokenStore();
    await pumpLogin(
      tester,
      store: store,
      identifier: 'student@example.com',
      password: 'wrong-password',
      response: http.Response(
        jsonEncode({
          'code': 'INVALID_CREDENTIALS',
          'message': 'Invalid credentials.',
          'timestamp': '2026-09-26T00:00:00Z',
          'requestId': 'req-1',
        }),
        401,
        headers: {'content-type': 'application/json'},
      ),
    );

    expect(find.text(english.t('errors.invalidCredentials')), findsOneWidget);
    expect(find.text('Invalid credentials.'), findsNothing);
    expect(store.values, isEmpty);
    expect(find.byKey(const Key('home')), findsNothing);
  });

  testWidgets('first launch is English and the language control switches both ways', (tester) async {
    final api = AuthApi(
      baseUrl: baseUrl,
      client: MockClient((request) async => http.Response('', 500)),
    );
    await tester.pumpWidget(CoachingApp(key: UniqueKey(), api: api, tokens: MemoryTokenStore()));
    await tester.pumpAndSettle();

    expect(find.widgetWithText(AppBar, 'Sign in'), findsOneWidget);
    expect(find.text('বাংলা'), findsOneWidget);

    await tester.tap(find.byKey(const Key('locale-toggle')));
    await tester.pump();

    expect(find.widgetWithText(AppBar, 'সাইন ইন'), findsOneWidget);
    expect(find.text('Sign in'), findsNothing);
    expect(find.text('ফোন বা ইমেইল'), findsOneWidget);

    await tester.tap(find.byKey(const Key('locale-toggle')));
    await tester.pump();

    expect(find.widgetWithText(AppBar, 'Sign in'), findsOneWidget);
    expect(find.text('সাইন ইন'), findsNothing);
  });

  testWidgets('master admin and system admin stay on login', (tester) async {
    for (final role in ['MASTER_ADMIN', 'SYSTEM_ADMIN']) {
      final store = MemoryTokenStore();
      await pumpLogin(
        tester,
        store: store,
        identifier: 'admin@example.com',
        password: 'password1',
        response: tokenResponse(role, refreshToken: 'refresh-body'),
      );

      expect(find.text(english.t('auth.studentsOnly')), findsOneWidget);
      expect(find.byKey(const Key('home')), findsNothing);
      expect(find.widgetWithText(AppBar, english.t('auth.loginTitle')), findsOneWidget);
      expect(store.values, isEmpty);
    }
  });
}
