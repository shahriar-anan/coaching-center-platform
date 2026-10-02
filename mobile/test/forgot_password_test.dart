import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:mobile/api/auth_api.dart';
import 'package:mobile/app.dart';
import 'package:mobile/i18n/en.dart';

import 'support/memory_token_store.dart';

void main() {
  const baseUrl = 'http://10.0.2.2:8080';

  testWidgets('the request message is the same whether or not the email exists', (tester) async {
    final requests = <http.Request>[];
    var apiMessage = 'no such account';
    final api = AuthApi(
      baseUrl: baseUrl,
      client: MockClient((request) async {
        requests.add(request);
        return http.Response(
          jsonEncode({'message': apiMessage}),
          200,
          headers: {'content-type': 'application/json'},
        );
      }),
    );
    final store = MemoryTokenStore();
    await tester.pumpWidget(CoachingApp(api: api, tokens: store));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('go-forgot')));
    await tester.pumpAndSettle();

    await tester.enterText(find.byKey(const Key('email')), 'missing@example.com');
    await tester.tap(find.byKey(const Key('forgot-submit')));
    await tester.pumpAndSettle();
    expect(find.text(english.t('auth.forgotSent')), findsOneWidget);
    expect(find.text('no such account'), findsNothing);
    expect(jsonDecode(requests.single.body), {'email': 'missing@example.com'});

    apiMessage = 'mail queued for a real account';
    await tester.enterText(find.byKey(const Key('email')), 'student@example.com');
    await tester.tap(find.byKey(const Key('forgot-submit')));
    await tester.pumpAndSettle();
    expect(find.text(english.t('auth.forgotSent')), findsOneWidget);
    expect(find.text('mail queued for a real account'), findsNothing);
    expect(find.text('no such account'), findsNothing);
  });

  testWidgets('reset sends the code and password, then requires sign-in', (tester) async {
    final requests = <http.Request>[];
    final api = AuthApi(
      baseUrl: baseUrl,
      client: MockClient((request) async {
        requests.add(request);
        if (request.url.path.endsWith('/auth/forgot-password')) {
          return http.Response(jsonEncode({'message': 'sent'}), 200, headers: {'content-type': 'application/json'});
        }
        return http.Response('', 204);
      }),
    );
    final store = MemoryTokenStore();
    await tester.pumpWidget(CoachingApp(api: api, tokens: store));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('go-forgot')));
    await tester.pumpAndSettle();
    await store.writeAccessToken('stale-access');
    await store.writeRefreshToken('stale-refresh');

    await tester.enterText(find.byKey(const Key('email')), 'student@example.com');
    await tester.tap(find.byKey(const Key('forgot-submit')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('reset-code')), '999111');
    await tester.enterText(find.byKey(const Key('password')), 'password2');
    await tester.enterText(find.byKey(const Key('confirm-password')), 'password2');
    await tester.tap(find.byKey(const Key('reset-submit')));
    await tester.pumpAndSettle();

    final reset = requests.singleWhere((request) => request.url.path.endsWith('/auth/reset-password'));
    expect(jsonDecode(reset.body), {'code': '999111', 'password': 'password2'});
    expect(reset.body.contains('confirm'), isFalse);
    expect(store.values, isEmpty);
    expect(find.byKey(const Key('home')), findsNothing);
    expect(find.widgetWithText(AppBar, english.t('auth.loginTitle')), findsOneWidget);
    expect(find.text(english.t('auth.resetDone')), findsOneWidget);
  });
}
