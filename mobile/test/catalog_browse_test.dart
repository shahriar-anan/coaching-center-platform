import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:mobile/api/auth_api.dart';
import 'package:mobile/app.dart';
import 'package:mobile/i18n/bn.dart';
import 'package:mobile/i18n/en.dart';

import 'support/catalog_http.dart';
import 'support/jwt.dart';
import 'support/memory_token_store.dart';

void main() {
  const baseUrl = 'http://10.0.2.2:8080';

  http.Response me() {
    return http.Response(
      jsonEncode({
        'id': 'user-1',
        'phone': '+8801700000002',
        'email': 'student@example.com',
        'emailVerifiedAt': null,
        'role': 'STUDENT',
        'status': 'ACTIVE',
        'fullName': 'Student',
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

  Future<List<http.Request>> pumpSignedIn(
    WidgetTester tester, {
    required MemoryTokenStore store,
    required http.Response Function(http.Request request) handler,
  }) async {
    final access = unsignedJwt(role: 'STUDENT');
    await store.writeAccessToken(access);
    await store.writeRefreshToken('refresh-1');
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

  testWidgets('the Courses tab requests GET /catalog/courses with q on search', (tester) async {
    final store = MemoryTokenStore();
    final requests = await pumpSignedIn(
      tester,
      store: store,
      handler: (request) {
        if (request.url.path.endsWith('/auth/refresh')) {
          return tokens(unsignedJwt(role: 'STUDENT'), 'refresh-1');
        }
        if (request.url.path.endsWith('/me')) {
          return me();
        }
        if (request.url.path.endsWith('/catalog/courses')) {
          return catalogPage([
            {
              'id': 'c1',
              'titleEn': 'Physics',
              'titleBn': 'পদার্থবিজ্ঞান',
              'price': 100,
              'deliveryMode': 'ONLINE',
              'accessType': 'FREEMIUM',
              'coverFileId': null,
            },
          ]);
        }
        if (request.url.path.endsWith('/catalog/books')) {
          return catalogPage([]);
        }
        return catalogError();
      },
    );

    expect(requests.any((r) => r.url.path.endsWith('/catalog/courses') && r.url.queryParameters['q'] == ''), isTrue);
    expect(find.text('Physics'), findsOneWidget);

    await tester.enterText(find.byKey(const Key('catalog-search-courses')), 'wave');
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();

    final search = requests.lastWhere((r) => r.url.path.endsWith('/catalog/courses'));
    expect(search.method, 'GET');
    expect(search.url.queryParameters['q'], 'wave');
  });

  testWidgets('the Library tab requests GET /catalog/books', (tester) async {
    final store = MemoryTokenStore();
    final requests = await pumpSignedIn(
      tester,
      store: store,
      handler: (request) {
        if (request.url.path.endsWith('/auth/refresh')) {
          return tokens(unsignedJwt(role: 'STUDENT'), 'refresh-1');
        }
        if (request.url.path.endsWith('/me')) {
          return me();
        }
        if (request.url.path.endsWith('/catalog/courses')) {
          return catalogPage([]);
        }
        if (request.url.path.endsWith('/catalog/books')) {
          return catalogPage([
            {
              'id': 'b1',
              'titleEn': 'Algebra Book',
              'titleBn': 'বীজগণিত',
              'author': 'Author',
              'price': 80,
              'coverFileId': null,
            },
          ]);
        }
        return catalogError();
      },
    );

    await tester.tap(find.byKey(const Key('home-tab-library')));
    await tester.pumpAndSettle();

    expect(requests.any((r) => r.url.path.endsWith('/catalog/books')), isTrue);
    expect(find.text('Algebra Book'), findsOneWidget);
  });

  testWidgets('a Bangla query is sent unchanged', (tester) async {
    final store = MemoryTokenStore();
    final requests = await pumpSignedIn(
      tester,
      store: store,
      handler: (request) {
        if (request.url.path.endsWith('/auth/refresh')) {
          return tokens(unsignedJwt(role: 'STUDENT'), 'refresh-1');
        }
        if (request.url.path.endsWith('/me')) {
          return me();
        }
        if (request.url.path.endsWith('/catalog/courses')) {
          return catalogPage([]);
        }
        if (request.url.path.endsWith('/catalog/books')) {
          return catalogPage([]);
        }
        return catalogError();
      },
    );

    await tester.tap(find.byKey(const Key('home-tab-library')));
    await tester.pumpAndSettle();

    const banglaQuery = 'উপলব্ধ';
    await tester.enterText(find.byKey(const Key('catalog-search-books')), banglaQuery);
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();

    final search = requests.lastWhere((r) => r.url.path.endsWith('/catalog/books'));
    expect(search.url.queryParameters['q'], banglaQuery);
  });

  testWidgets('an empty list renders the empty catalog string', (tester) async {
    final store = MemoryTokenStore();
    await pumpSignedIn(
      tester,
      store: store,
      handler: (request) {
        if (request.url.path.endsWith('/auth/refresh')) {
          return tokens(unsignedJwt(role: 'STUDENT'), 'refresh-1');
        }
        if (request.url.path.endsWith('/me')) {
          return me();
        }
        if (request.url.path.endsWith('/catalog/courses')) {
          return catalogPage([]);
        }
        if (request.url.path.endsWith('/catalog/books')) {
          return catalogPage([]);
        }
        return catalogError();
      },
    );

    expect(find.byKey(const Key('catalog-empty')), findsOneWidget);
    expect(find.text(english.t('catalog.empty')), findsOneWidget);
  });

  testWidgets('a load failure renders the catalog load-failed string', (tester) async {
    final store = MemoryTokenStore();
    await pumpSignedIn(
      tester,
      store: store,
      handler: (request) {
        if (request.url.path.endsWith('/auth/refresh')) {
          return tokens(unsignedJwt(role: 'STUDENT'), 'refresh-1');
        }
        if (request.url.path.endsWith('/me')) {
          return me();
        }
        if (request.url.path.endsWith('/catalog/courses')) {
          return catalogError();
        }
        return catalogError();
      },
    );

    expect(find.byKey(const Key('catalog-error')), findsOneWidget);
    expect(find.text(english.t('catalog.loadFailed')), findsOneWidget);
  });

  testWidgets('catalog strings exist in Bangla', (tester) async {
    expect(bangla.t('catalog.empty'), isNot(english.t('catalog.empty')));
    expect(bangla.t('catalog.loadFailed'), isNot(english.t('catalog.loadFailed')));
  });
}
