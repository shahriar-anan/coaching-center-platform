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

  http.Response error(String code) {
    return http.Response(
      jsonEncode({'code': code, 'message': 'raw api message', 'timestamp': '2026-09-30T00:00:00Z', 'requestId': 'req-1'}),
      409,
      headers: {'content-type': 'application/json'},
    );
  }

  Future<List<http.Request>> openRegister(
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
    await tester.tap(find.byKey(const Key('go-register')));
    await tester.pumpAndSettle();
    return requests;
  }

  Future<void> fillAccount(
    WidgetTester tester, {
    required String password,
    required String confirm,
  }) async {
    await tester.enterText(find.byKey(const Key('full-name')), 'Rahim Ahmed');
    await tester.enterText(find.byKey(const Key('phone')), '01700000002');
    await tester.enterText(find.byKey(const Key('email')), 'rahim@example.com');
    await tester.enterText(find.byKey(const Key('password')), password);
    await tester.enterText(find.byKey(const Key('confirm-password')), confirm);
  }

  testWidgets('the form has name, phone, email, password, and confirm password', (tester) async {
    await openRegister(tester, handler: (request) => http.Response('', 500));

    expect(find.byKey(const Key('full-name')), findsOneWidget);
    expect(find.byKey(const Key('phone')), findsOneWidget);
    expect(find.byKey(const Key('email')), findsOneWidget);
    expect(find.byKey(const Key('password')), findsOneWidget);
    expect(find.byKey(const Key('confirm-password')), findsOneWidget);
  });

  testWidgets('mismatched passwords do not call the API', (tester) async {
    var called = false;
    await openRegister(
      tester,
      handler: (request) {
        called = true;
        return http.Response('', 500);
      },
    );
    await fillAccount(tester, password: 'password1', confirm: 'password2');
    await tester.tap(find.byKey(const Key('register-submit')));
    await tester.pumpAndSettle();

    expect(called, isFalse);
    expect(find.text(english.t('auth.passwordMismatch')), findsOneWidget);
  });

  testWidgets('submit sends the account fields and a 201 can still sign in', (tester) async {
    final requests = await openRegister(
      tester,
      handler: (request) {
        if (request.url.path.endsWith('/auth/register')) {
          return http.Response(jsonEncode({'fullName': 'Rahim Ahmed'}), 201, headers: {'content-type': 'application/json'});
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
            jsonEncode({
              'fullName': 'Rahim Ahmed',
              'phone': '+8801700000002',
              'email': 'rahim@example.com',
              'role': 'STUDENT',
              'status': 'ACTIVE',
            }),
            200,
            headers: {'content-type': 'application/json'},
          );
        }
        return http.Response('', 500);
      },
    );
    await fillAccount(tester, password: 'password1', confirm: 'password1');
    await tester.tap(find.byKey(const Key('register-submit')));
    await tester.pumpAndSettle();

    final register = requests.singleWhere((request) => request.url.path.endsWith('/auth/register'));
    expect(jsonDecode(register.body), {
      'fullName': 'Rahim Ahmed',
      'phone': '01700000002',
      'email': 'rahim@example.com',
      'password': 'password1',
    });
    expect(register.body.contains('confirm'), isFalse);
    expect(find.byKey(const Key('verification-code')), findsOneWidget);

    await tester.tap(find.byKey(const Key('leave-register')));
    await tester.pumpAndSettle();
    expect(find.widgetWithText(AppBar, english.t('auth.loginTitle')), findsOneWidget);

    await tester.enterText(find.byKey(const Key('identifier')), 'rahim@example.com');
    await tester.enterText(find.byKey(const Key('password')), 'password1');
    await tester.tap(find.byKey(const Key('login-submit')));
    await tester.pumpAndSettle();
    expect(find.text('Rahim Ahmed'), findsOneWidget);
  });

  testWidgets('verify and resend send the code and the email', (tester) async {
    final requests = await openRegister(
      tester,
      handler: (request) {
        if (request.url.path.endsWith('/auth/register')) {
          return http.Response('{}', 201);
        }
        if (request.url.path.endsWith('/auth/verify-email/resend')) {
          return http.Response(jsonEncode({'message': 'sent'}), 200, headers: {'content-type': 'application/json'});
        }
        return http.Response('', 204);
      },
    );
    await fillAccount(tester, password: 'password1', confirm: 'password1');
    await tester.tap(find.byKey(const Key('register-submit')));
    await tester.pumpAndSettle();

    await tester.enterText(find.byKey(const Key('verification-code')), '123456');
    await tester.tap(find.byKey(const Key('verify-submit')));
    await tester.pumpAndSettle();
    final verify = requests.singleWhere((request) => request.url.path.endsWith('/auth/verify-email'));
    expect(jsonDecode(verify.body), {'code': '123456'});
    expect(find.text(english.t('auth.verified')), findsOneWidget);

    await tester.tap(find.byKey(const Key('resend')));
    await tester.pumpAndSettle();
    final resend = requests.singleWhere((request) => request.url.path.endsWith('/auth/verify-email/resend'));
    expect(jsonDecode(resend.body), {'email': 'rahim@example.com'});
    expect(find.text(english.t('auth.resendSent')), findsOneWidget);
  });

  testWidgets('duplicate phone and email render translated errors', (tester) async {
    for (final code in ['DUPLICATE_PHONE', 'DUPLICATE_EMAIL']) {
      await openRegister(tester, handler: (request) => error(code));
      await fillAccount(tester, password: 'password1', confirm: 'password1');
      await tester.tap(find.byKey(const Key('register-submit')));
      await tester.pumpAndSettle();

      expect(find.text(english.t(code == 'DUPLICATE_PHONE' ? 'errors.duplicatePhone' : 'errors.duplicateEmail')), findsOneWidget);
      expect(find.text('raw api message'), findsNothing);
    }
  });
}
