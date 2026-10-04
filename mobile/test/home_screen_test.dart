import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:mobile/api/auth_api.dart';
import 'package:mobile/app.dart';
import 'package:mobile/i18n/en.dart';

import 'support/catalog_http.dart';
import 'support/jwt.dart';
import 'support/memory_token_store.dart';

void main() {
  const baseUrl = 'http://10.0.2.2:8080';

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

  http.Response tokens(String accessToken, String refreshToken) {
    return http.Response(
      jsonEncode({'accessToken': accessToken, 'expiresInSeconds': 900, 'refreshToken': refreshToken}),
      200,
      headers: {'content-type': 'application/json'},
    );
  }

  Future<void> openHome(
    WidgetTester tester, {
    required MemoryTokenStore store,
    String fullName = 'Amina Khan',
  }) async {
    final access = unsignedJwt(role: 'STUDENT');
    await store.writeAccessToken(access);
    await store.writeRefreshToken('refresh-1');
    final api = AuthApi(
      baseUrl: baseUrl,
      client: MockClient((request) async {
        if (request.url.path.endsWith('/auth/refresh')) {
          return tokens(access, 'refresh-1');
        }
        if (request.url.path.endsWith('/me')) {
          return me(fullName);
        }
        final catalog = tryCatalogList(request);
        if (catalog != null) {
          return catalog;
        }
        return http.Response('', 500);
      }),
    );
    await tester.pumpWidget(CoachingApp(key: UniqueKey(), api: api, tokens: store));
    await tester.pumpAndSettle();
  }

  testWidgets('the signed-in home shows the name from GET /me and Courses and Library tabs', (tester) async {
    final store = MemoryTokenStore();
    await openHome(tester, store: store, fullName: 'Amina Khan');

    expect(find.byKey(const Key('home')), findsOneWidget);
    expect(find.byKey(const Key('home-name')), findsOneWidget);
    expect(find.text('Amina Khan'), findsOneWidget);
    expect(find.byKey(const Key('home-tab-courses')), findsOneWidget);
    expect(find.byKey(const Key('home-tab-library')), findsOneWidget);
    expect(find.byKey(const Key('home-courses-pane')), findsOneWidget);
    expect(find.text(english.t('catalog.tabCourses')), findsWidgets);
  });

  testWidgets('switching tabs shows the library pane', (tester) async {
    final store = MemoryTokenStore();
    await openHome(tester, store: store);

    await tester.tap(find.byKey(const Key('home-tab-library')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('home-library-pane')), findsOneWidget);
    expect(find.text(english.t('catalog.tabLibrary')), findsWidgets);
  });

  testWidgets('the home is absent when signed out', (tester) async {
    final store = MemoryTokenStore();
    final api = AuthApi(
      baseUrl: baseUrl,
      client: MockClient((request) async => http.Response('', 500)),
    );
    await tester.pumpWidget(CoachingApp(key: UniqueKey(), api: api, tokens: store));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('home')), findsNothing);
    expect(find.widgetWithText(AppBar, english.t('auth.loginTitle')), findsOneWidget);
  });

  testWidgets('logout clears the session and removes the home', (tester) async {
    final store = MemoryTokenStore();
    final access = unsignedJwt(role: 'STUDENT');
    await store.writeAccessToken(access);
    await store.writeRefreshToken('refresh-1');
    final api = AuthApi(
      baseUrl: baseUrl,
      client: MockClient((request) async {
        if (request.url.path.endsWith('/auth/refresh')) {
          return tokens(access, 'refresh-1');
        }
        if (request.url.path.endsWith('/me')) {
          return me('Amina Khan');
        }
        if (request.url.path.endsWith('/auth/logout')) {
          return http.Response('', 204);
        }
        final catalog = tryCatalogList(request);
        if (catalog != null) {
          return catalog;
        }
        return http.Response('', 500);
      }),
    );
    await tester.pumpWidget(CoachingApp(key: UniqueKey(), api: api, tokens: store));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('logout')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('home')), findsNothing);
    expect(store.values, isEmpty);
  });
}
