import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:mobile/api/auth_api.dart';
import 'package:mobile/app.dart';
import 'support/book_fixtures.dart';
import 'support/catalog_http.dart';
import 'support/jwt.dart';
import 'support/memory_token_store.dart';

void main() {
  const baseUrl = 'http://10.0.2.2:8080';

  http.Response me() {
    return http.Response(
      jsonEncode({'id': 'user-1', 'fullName': 'Student', 'role': 'STUDENT', 'status': 'ACTIVE'}),
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

  testWidgets('book detail shows titles, author, description, price, and cover', (tester) async {
    final store = MemoryTokenStore();
    final access = unsignedJwt(role: 'STUDENT');
    await store.writeAccessToken(access);
    await store.writeRefreshToken('refresh-1');
    final api = AuthApi(
      baseUrl: baseUrl,
      client: MockClient((request) async {
        final catalog = tryCatalogList(request);
        if (catalog != null) {
          if (request.url.path.endsWith('/catalog/books')) {
            return catalogPage([bookSummaryJson(id: bookId, titleEn: 'Sample Book')]);
          }
          return catalog;
        }
        if (request.url.path.endsWith('/auth/refresh')) {
          return tokens(unsignedJwt(role: 'STUDENT'), 'refresh-1');
        }
        if (request.url.path.endsWith('/me')) {
          return me();
        }
        if (request.url.path.endsWith('/catalog/books/$bookId')) {
          return bookDetailResponse(id: bookId);
        }
        if (request.url.path.endsWith('/files/$bookCoverFileId')) {
          return tinyPngBytes();
        }
        return catalogError();
      }),
    );
    await tester.pumpWidget(CoachingApp(key: UniqueKey(), api: api, tokens: store));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('home-tab-library')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Sample Book'));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('book-detail')), findsOneWidget);
    expect(find.byKey(const Key('book-cover')), findsOneWidget);
    expect(find.text('Sample Book'), findsWidgets);
    expect(find.text('নমুনা বই'), findsOneWidget);
    expect(find.text('Author Name'), findsOneWidget);
    expect(find.text('Book description text'), findsOneWidget);
    expect(find.textContaining('80'), findsOneWidget);
    expect(find.byKey(const Key('book-order')), findsOneWidget);
  });
}
