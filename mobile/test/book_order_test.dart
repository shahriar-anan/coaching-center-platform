import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:mobile/api/auth_api.dart';
import 'package:mobile/api/catalog_models.dart';
import 'package:mobile/i18n/en.dart';
import 'package:mobile/screens/book_order_screen.dart';

import 'support/book_fixtures.dart';
import 'support/jwt.dart';
import 'support/memory_token_store.dart';

void main() {
  const baseUrl = 'http://10.0.2.2:8080';

  setUp(() {
    TestWidgetsFlutterBinding.ensureInitialized();
  });

  Future<List<http.Request>> pumpOrder(
    WidgetTester tester, {
    required BookDetail book,
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
    tester.view.physicalSize = const Size(800, 1200);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      MaterialApp(
        home: BookOrderScreen(api: api, messages: english, book: book),
      ),
    );
    await tester.pumpAndSettle();
    return requests;
  }

  Future<void> choosePickupOnline(WidgetTester tester) async {
    await tester.tap(find.text(english.t('bookOrder.pickup')));
    await tester.pumpAndSettle();
    await tester.tap(find.text(english.t('bookOrder.paymentOnline')));
    await tester.pumpAndSettle();
  }

  Future<void> selectDelivery(WidgetTester tester) async {
    await tester.tap(find.text(english.t('bookOrder.delivery')));
    await tester.pumpAndSettle();
  }

  Future<void> selectCash(WidgetTester tester) async {
    await tester.tap(find.text(english.t('bookOrder.paymentCash')));
    await tester.pumpAndSettle();
  }

  testWidgets('pickup hides address fields and delivery submit omits address', (tester) async {
    final requests = await pumpOrder(tester, book: bookForOrder(), handler: (request) async {
      if (request.url.path.endsWith('/me/orders')) {
        return http.Response('[]', 200, headers: {'content-type': 'application/json'});
      }
      if (request.url.path.endsWith('/orders') && request.method == 'POST') {
        return bookOrderCreated();
      }
      return http.Response('', 404);
    });

    expect(find.byKey(const Key('book-order-address')), findsNothing);
    await choosePickupOnline(tester);
    await tester.enterText(find.byKey(const Key('book-order-phone')), '+8801711111111');
    await tester.enterText(find.byKey(const Key('book-order-transaction-ref')), 'trx-pickup');
    await tester.ensureVisible(find.byKey(const Key('book-order-submit')));
    await tester.tap(find.byKey(const Key('book-order-submit')));
    await tester.pumpAndSettle();

    final post = requests.firstWhere((r) => r.method == 'POST' && r.url.path.contains('/books/'));
    final body = jsonDecode(post.body) as Map<String, dynamic>;
    expect(body['fulfillment'], 'PICKUP');
    expect(body.containsKey('address'), isFalse);
    expect(body['phone'], '+8801711111111');
  });

  testWidgets('delivery shows address and optional area in the request body', (tester) async {
    final requests = await pumpOrder(tester, book: bookForOrder(), handler: (request) async {
      if (request.url.path.endsWith('/me/orders')) {
        return http.Response('[]', 200, headers: {'content-type': 'application/json'});
      }
      if (request.url.path.endsWith('/orders') && request.method == 'POST') {
        return bookOrderCreated();
      }
      return http.Response('', 404);
    });

    await choosePickupOnline(tester);
    await selectDelivery(tester);
    expect(find.byKey(const Key('book-order-address')), findsOneWidget);
    expect(find.byKey(const Key('book-order-area')), findsOneWidget);

    await tester.enterText(find.byKey(const Key('book-order-phone')), '+8801711111111');
    await tester.enterText(find.byKey(const Key('book-order-address')), 'House 1, Road 2');
    await tester.enterText(find.byKey(const Key('book-order-area')), 'Dhaka');
    await tester.enterText(find.byKey(const Key('book-order-transaction-ref')), 'trx-del');
    await tester.ensureVisible(find.byKey(const Key('book-order-submit')));
    await tester.tap(find.byKey(const Key('book-order-submit')));
    await tester.pumpAndSettle();

    final post = requests.firstWhere((r) => r.method == 'POST' && r.url.path.contains('/books/'));
    final body = jsonDecode(post.body) as Map<String, dynamic>;
    expect(body['fulfillment'], 'DELIVERY');
    expect(body['address'], 'House 1, Road 2');
    expect(body['area'], 'Dhaka');
    expect(body['method'], 'BKASH');
    expect(body['transactionRef'], 'trx-del');
  });

  testWidgets('cash and delivery together show an error and block submit', (tester) async {
    await pumpOrder(tester, book: bookForOrder(), handler: (request) async {
      if (request.url.path.endsWith('/me/orders')) {
        return http.Response('[]', 200, headers: {'content-type': 'application/json'});
      }
      return http.Response('', 404);
    });

    await selectDelivery(tester);
    expect(find.text(english.t('bookOrder.paymentCash')), findsOneWidget);
    await selectCash(tester);
    expect(find.byKey(const Key('book-order-cash-delivery-error')), findsOneWidget);
    expect(find.byKey(const Key('book-order-submit')), findsNothing);
    expect(find.byKey(const Key('book-order-phone')), findsNothing);
  });

  testWidgets('successful order shows status in history from GET /me/orders', (tester) async {
    const orderId = 'order-42';
    await pumpOrder(tester, book: bookForOrder(), handler: (request) async {
      if (request.url.path.endsWith('/me/orders') && request.method == 'GET') {
        return bookOrderHistoryList(orderId: orderId, status: 'PLACED');
      }
      if (request.url.path.endsWith('/orders') && request.method == 'POST') {
        return bookOrderCreated(orderId: orderId);
      }
      return http.Response('[]', 200, headers: {'content-type': 'application/json'});
    });

    await choosePickupOnline(tester);
    await tester.enterText(find.byKey(const Key('book-order-phone')), '+8801711111111');
    await tester.enterText(find.byKey(const Key('book-order-transaction-ref')), 'trx-hist');
    await tester.tap(find.byKey(const Key('book-order-submit')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('book-order-history-$orderId')), findsOneWidget);
    expect(find.textContaining('PLACED'), findsOneWidget);
  });

  testWidgets('the order screen has no cancel action', (tester) async {
    await pumpOrder(tester, book: bookForOrder(), handler: (request) async {
      if (request.url.path.endsWith('/me/orders')) {
        return http.Response('[]', 200, headers: {'content-type': 'application/json'});
      }
      return http.Response('', 404);
    });

    expect(find.byKey(const Key('book-order-cancel')), findsNothing);
  });
}
