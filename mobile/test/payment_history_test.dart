import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:mobile/api/auth_api.dart';
import 'package:mobile/i18n/en.dart';
import 'package:mobile/screens/payment_history_screen.dart';

import 'support/course_fixtures.dart';
import 'support/jwt.dart';
import 'support/memory_token_store.dart';

void main() {
  const baseUrl = 'http://10.0.2.2:8080';

  Future<void> openHistory(
    WidgetTester tester, {
    required Future<http.Response> Function(http.Request request) handler,
  }) async {
    final store = MemoryTokenStore();
    await store.writeAccessToken(unsignedJwt(role: 'STUDENT'));
    await store.writeRefreshToken('refresh-1');
    final api = AuthApi(
      baseUrl: baseUrl,
      client: MockClient(handler),
    );
    api.bind(tokens: store, onSessionCleared: () {});
    await tester.pumpWidget(
      MaterialApp(
        home: PaymentHistoryScreen(api: api, messages: english, localeCode: 'en'),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('lists the caller course payments with the course title and status', (tester) async {
    await openHistory(tester, handler: (request) async {
      if (request.url.path.endsWith('/me/purchases')) {
        return purchaseHistoryList(courseId: courseId, transactionRef: 'trx-pending');
      }
      if (request.url.path.endsWith('/catalog/courses/$courseId')) {
        return courseDetailResponse(id: courseId, accessType: 'FREEMIUM', tree: const []);
      }
      return http.Response('', 404);
    });

    expect(find.byKey(const Key('payment-history-enrollment-1')), findsOneWidget);
    expect(find.text('Freemium Course'), findsOneWidget);
    expect(find.textContaining(english.t('purchase.statusPending')), findsOneWidget);
    expect(find.textContaining('trx-pending'), findsOneWidget);
    expect(find.textContaining('100'), findsOneWidget);
  });

  testWidgets('an empty purchase list says there are no payments', (tester) async {
    await openHistory(tester, handler: (request) async {
      if (request.url.path.endsWith('/me/purchases')) {
        return http.Response('[]', 200, headers: {'content-type': 'application/json'});
      }
      return http.Response('', 404);
    });

    expect(find.text(english.t('purchase.emptyHistory')), findsOneWidget);
  });
}
