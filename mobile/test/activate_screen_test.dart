import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:mobile/api/auth_api.dart';
import 'package:mobile/api/error_text.dart';
import 'package:mobile/app.dart';
import 'package:mobile/i18n/en.dart';

import 'support/jwt.dart';
import 'support/memory_token_store.dart';

void main() {
  const baseUrl = 'http://10.0.2.2:8080';

  Future<List<http.Request>> openActivate(
    WidgetTester tester, {
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
    await tester.pumpWidget(CoachingApp(key: UniqueKey(), api: api, tokens: MemoryTokenStore()));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('go-activate')));
    await tester.pumpAndSettle();
    return requests;
  }

  Future<void> fill(WidgetTester tester, {String confirm = 'password1'}) async {
    await tester.enterText(find.byKey(const Key('phone')), '01700000002');
    await tester.enterText(find.byKey(const Key('activation-code')), '654321');
    await tester.enterText(find.byKey(const Key('password')), 'password1');
    await tester.enterText(find.byKey(const Key('confirm-password')), confirm);
  }

  testWidgets('the form has phone, code, password, and confirm password, and no name', (tester) async {
    await openActivate(tester, handler: (request) => http.Response('', 500));

    expect(find.byKey(const Key('phone')), findsOneWidget);
    expect(find.byKey(const Key('activation-code')), findsOneWidget);
    expect(find.byKey(const Key('password')), findsOneWidget);
    expect(find.byKey(const Key('confirm-password')), findsOneWidget);
    expect(find.byKey(const Key('full-name')), findsNothing);
    expect(find.text(english.t('auth.fullName')), findsNothing);
  });

  testWidgets('submit sends phone, code, and password, then signs in', (tester) async {
    final requests = await openActivate(
      tester,
      handler: (request) {
        if (request.url.path.endsWith('/auth/activate')) {
          return http.Response('', 204);
        }
        if (request.url.path.endsWith('/auth/login')) {
          return http.Response(
            jsonEncode({
              'accessToken': unsignedJwt(role: 'STUDENT'),
              'expiresInSeconds': 900,
              'refreshToken': 'refresh-body',
            }),
            200,
            headers: {'content-type': 'application/json'},
          );
        }
        if (request.url.path.endsWith('/me')) {
          return http.Response(
            jsonEncode({'fullName': 'Activated Student', 'role': 'STUDENT', 'status': 'ACTIVE'}),
            200,
            headers: {'content-type': 'application/json'},
          );
        }
        return http.Response('', 500);
      },
    );
    await fill(tester);
    await tester.tap(find.byKey(const Key('activate-submit')));
    await tester.pumpAndSettle();

    final activate = requests.singleWhere((request) => request.url.path.endsWith('/auth/activate'));
    expect(jsonDecode(activate.body), {'phone': '01700000002', 'code': '654321', 'password': 'password1'});
    expect(activate.body.contains('fullName'), isFalse);
    expect(activate.body.contains('confirm'), isFalse);
    final login = requests.singleWhere((request) => request.url.path.endsWith('/auth/login'));
    expect(jsonDecode(login.body), {'identifier': '01700000002', 'password': 'password1'});
    expect(find.text('Activated Student'), findsOneWidget);
    expect(find.byKey(const Key('home')), findsOneWidget);
  });

  testWidgets('activation code errors render distinct translated text', (tester) async {
    const codes = ['INVALID_CODE', 'CODE_EXPIRED', 'CODE_ALREADY_USED', 'CODE_ATTEMPTS_EXCEEDED'];
    expect(codes.map(errorMessageKey).map(english.t).toSet().length, 4);

    for (final code in codes) {
      await openActivate(
        tester,
        handler: (request) {
          return http.Response(
            jsonEncode({'code': code, 'message': 'Code is no longer valid.', 'timestamp': '2026-09-30T00:00:00Z'}),
            400,
            headers: {'content-type': 'application/json'},
          );
        },
      );
      await fill(tester);
      await tester.tap(find.byKey(const Key('activate-submit')));
      await tester.pumpAndSettle();

      expect(find.text(english.t(errorMessageKey(code))), findsOneWidget);
      expect(find.text('Code is no longer valid.'), findsNothing);
      expect(find.byKey(const Key('home')), findsNothing);
    }
  });
}
