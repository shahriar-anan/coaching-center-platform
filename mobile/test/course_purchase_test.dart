import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:mobile/api/auth_api.dart';
import 'package:mobile/api/catalog_models.dart';
import 'package:mobile/api/error_text.dart';
import 'package:mobile/i18n/en.dart';
import 'package:mobile/screens/course_purchase_screen.dart';

import 'support/course_fixtures.dart';
import 'support/jwt.dart';
import 'support/memory_token_store.dart';

void main() {
  const baseUrl = 'http://10.0.2.2:8080';

  Future<List<http.Request>> pumpPurchase(
    WidgetTester tester, {
    required CourseDetail course,
    required Future<http.Response> Function(http.Request request) handler,
  }) async {
    final store = MemoryTokenStore();
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
    api.bind(tokens: store, onSessionCleared: () {});
    await tester.pumpWidget(
      MaterialApp(
        home: CoursePurchaseScreen(api: api, messages: english, course: course),
      ),
    );
    await tester.pumpAndSettle();
    return requests;
  }

  Future<void> submitPurchase(WidgetTester tester, {required String transactionRef}) async {
    await tester.enterText(find.byKey(const Key('purchase-transaction-ref')), transactionRef);
    await tester.tap(find.byKey(const Key('purchase-submit')));
    await tester.pumpAndSettle();
  }

  testWidgets('purchase sends method and transactionRef without deliveryMode for an online course', (tester) async {
    final course = courseForPurchase();
    final requests = await pumpPurchase(tester, course: course, handler: (request) async {
      if (request.url.path.endsWith('/me/purchases')) {
        return http.Response('[]', 200, headers: {'content-type': 'application/json'});
      }
      if (request.url.path.endsWith('/purchases') && request.method == 'POST') {
        return purchaseCreated(transactionRef: 'trx-online');
      }
      return http.Response('', 404);
    });

    await submitPurchase(tester, transactionRef: 'trx-online');

    final post = requests.firstWhere((r) => r.method == 'POST' && r.url.path.endsWith('/purchases'));
    final body = jsonDecode(post.body) as Map<String, dynamic>;
    expect(body['method'], 'BKASH');
    expect(body['transactionRef'], 'trx-online');
    expect(body.containsKey('deliveryMode'), isFalse);
  });

  testWidgets('a hybrid course adds deliveryMode to the purchase body', (tester) async {
    final course = courseForPurchase(deliveryMode: 'HYBRID');
    final requests = await pumpPurchase(tester, course: course, handler: (request) async {
      if (request.url.path.endsWith('/me/purchases')) {
        return http.Response('[]', 200, headers: {'content-type': 'application/json'});
      }
      if (request.url.path.endsWith('/purchases') && request.method == 'POST') {
        return purchaseCreated(transactionRef: 'trx-hybrid');
      }
      return http.Response('', 404);
    });

    expect(find.byKey(const Key('purchase-delivery-mode')), findsOneWidget);
    await submitPurchase(tester, transactionRef: 'trx-hybrid');

    final post = requests.firstWhere((r) => r.method == 'POST' && r.url.path.endsWith('/purchases'));
    final body = jsonDecode(post.body) as Map<String, dynamic>;
    expect(body['deliveryMode'], 'ONLINE');
  });

  testWidgets('successful purchase shows the payment number and a pending history row', (tester) async {
    const paymentNumber = '01811112222';
    const transactionRef = 'trx-pending';
    final course = courseForPurchase();
    await pumpPurchase(tester, course: course, handler: (request) async {
      if (request.url.path.endsWith('/me/purchases')) {
        if (request.method == 'GET') {
          return purchaseHistoryList(courseId: courseId, transactionRef: transactionRef);
        }
      }
      if (request.url.path.endsWith('/purchases') && request.method == 'POST') {
        return purchaseCreated(paymentNumber: paymentNumber, transactionRef: transactionRef);
      }
      return http.Response('[]', 200, headers: {'content-type': 'application/json'});
    });

    await submitPurchase(tester, transactionRef: transactionRef);

    expect(find.byKey(const Key('purchase-payment-number')), findsOneWidget);
    expect(find.text(paymentNumber), findsOneWidget);
    expect(find.text(english.t('purchase.statusPending')), findsOneWidget);
    expect(find.byKey(const Key('purchase-history-enrollment-1')), findsOneWidget);
  });

  testWidgets('ALREADY_ENROLLED renders a translated error', (tester) async {
    await pumpPurchase(tester, course: courseForPurchase(), handler: (request) async {
      if (request.url.path.endsWith('/me/purchases')) {
        return http.Response('[]', 200, headers: {'content-type': 'application/json'});
      }
      if (request.url.path.endsWith('/purchases')) {
        return apiConflict('ALREADY_ENROLLED');
      }
      return http.Response('', 404);
    });

    await submitPurchase(tester, transactionRef: 'trx-dup');
    expect(find.text(english.t(errorMessageKey('ALREADY_ENROLLED'))), findsOneWidget);
  });

  testWidgets('COURSE_IS_FREE renders a translated error', (tester) async {
    await pumpPurchase(tester, course: courseForPurchase(), handler: (request) async {
      if (request.url.path.endsWith('/me/purchases')) {
        return http.Response('[]', 200, headers: {'content-type': 'application/json'});
      }
      if (request.url.path.endsWith('/purchases')) {
        return apiConflict('COURSE_IS_FREE');
      }
      return http.Response('', 404);
    });

    await submitPurchase(tester, transactionRef: 'trx-free');
    expect(find.text(english.t(errorMessageKey('COURSE_IS_FREE'))), findsOneWidget);
  });

  testWidgets('the purchase screen has no cancel action', (tester) async {
    await pumpPurchase(tester, course: courseForPurchase(), handler: (request) async {
      if (request.url.path.endsWith('/me/purchases')) {
        return http.Response('[]', 200, headers: {'content-type': 'application/json'});
      }
      return http.Response('', 404);
    });

    expect(find.byKey(const Key('purchase-cancel')), findsNothing);
  });
}
