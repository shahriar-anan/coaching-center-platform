import 'dart:convert';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:integration_test/integration_test.dart';
import 'package:mobile/api/auth_api.dart';
import 'package:mobile/app.dart';
import 'package:mobile/auth/secure_token_store.dart';
import 'package:mobile/i18n/en.dart';

const _baseUrl = String.fromEnvironment('API_BASE_URL', defaultValue: 'http://10.0.2.2:8080');
const _masterPhone = String.fromEnvironment('MASTER_PHONE');
const _masterPassword = String.fromEnvironment('MASTER_PASSWORD');

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('student signs in with phone and email against the API', (tester) async {
    final phone = _uniquePhone();
    final email = 'spike-${phone.substring(1)}@example.com';
    const password = 'password1';
    await _register(phone, email, password);

    final recorder = _RecordingClient(http.Client());
    final store = SecureTokenStore();
    final api = AuthApi(baseUrl: _baseUrl, client: recorder);

    await _signIn(tester, api: api, store: store, identifier: phone, password: password);
    expect(find.text('Spike Student'), findsOneWidget);
    await _expectStoredRefreshMatchesBody(store, recorder);

    await _signIn(tester, api: api, store: store, identifier: email, password: password);
    expect(find.text('Spike Student'), findsOneWidget);
    await _expectStoredRefreshMatchesBody(store, recorder);

    await _signIn(tester, api: api, store: store, identifier: email, password: 'wrong-password');
    expect(find.text(english.t('errors.invalidCredentials')), findsOneWidget);
    expect(find.text('Spike Student'), findsNothing);
    expect(await store.readRefreshToken(), isNull);

    final pendingPhone = _uniquePhone();
    await _createPendingStudent(pendingPhone, 'pending-${pendingPhone.substring(1)}@example.com');
    await _signIn(tester, api: api, store: store, identifier: pendingPhone, password: password);
    expect(find.text(english.t('errors.invalidCredentials')), findsOneWidget);
    expect(find.byKey(const Key('home')), findsNothing);
    expect(await store.readAccessToken(), isNull);
  });
}

Future<void> _signIn(
  WidgetTester tester, {
  required AuthApi api,
  required SecureTokenStore store,
  required String identifier,
  required String password,
}) async {
  await store.clear();
  await tester.pumpWidget(CoachingApp(api: api, tokens: store));
  await tester.pumpAndSettle();
  await tester.enterText(find.byKey(const Key('identifier')), identifier);
  await tester.enterText(find.byKey(const Key('password')), password);
  await tester.tap(find.byKey(const Key('login-submit')));
  await tester.pumpAndSettle(const Duration(milliseconds: 100), EnginePhase.sendSemanticsUpdate, const Duration(seconds: 30));
}

Future<void> _expectStoredRefreshMatchesBody(SecureTokenStore store, _RecordingClient recorder) async {
  expect(recorder.statusCode, 200);
  expect(recorder.refreshToken, isNotEmpty);
  expect(recorder.setCookie?.toLowerCase() ?? '', isNot(contains('refresh')));
  expect(await store.readRefreshToken(), recorder.refreshToken);
  expect(await store.readAccessToken(), isNotEmpty);
}

Future<void> _register(String phone, String email, String password) async {
  final response = await http.post(
    Uri.parse('$_baseUrl/api/v1/auth/register'),
    headers: const {'Content-Type': 'application/json'},
    body: jsonEncode({'fullName': 'Spike Student', 'phone': phone, 'email': email, 'password': password}),
  );
  expect(response.statusCode, 201, reason: 'register ${response.statusCode}');
}

Future<void> _createPendingStudent(String phone, String email) async {
  expect(_masterPhone, isNotEmpty);
  expect(_masterPassword, isNotEmpty);
  final login = await http.post(
    Uri.parse('$_baseUrl/api/v1/auth/login'),
    headers: const {'Content-Type': 'application/json', 'X-Client-Type': 'mobile'},
    body: jsonEncode({'identifier': _masterPhone, 'password': _masterPassword}),
  );
  expect(login.statusCode, 200, reason: 'master login ${login.statusCode}');
  final accessToken = (jsonDecode(login.body) as Map<String, dynamic>)['accessToken'] as String;
  final created = await http.post(
    Uri.parse('$_baseUrl/api/v1/students'),
    headers: {'Content-Type': 'application/json', 'Authorization': 'Bearer $accessToken'},
    body: jsonEncode({'fullName': 'Pending Student', 'phone': phone, 'email': email}),
  );
  expect(created.statusCode, 201, reason: 'create student ${created.statusCode}');
}

String _uniquePhone() {
  final random = Random();
  final suffix = List.generate(9, (_) => random.nextInt(10)).join();
  return '+8801$suffix';
}

class _RecordingClient extends http.BaseClient {
  _RecordingClient(this._inner);

  final http.Client _inner;
  int? statusCode;
  String? refreshToken;
  String? setCookie;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    final response = await _inner.send(request);
    final bytes = await response.stream.toBytes();
    statusCode = response.statusCode;
    setCookie = response.headers['set-cookie'];
    if (response.statusCode == 200 && response.request?.url.path.endsWith('/auth/login') == true) {
      final decoded = jsonDecode(utf8.decode(bytes));
      if (decoded is Map && decoded['refreshToken'] is String) {
        refreshToken = decoded['refreshToken'] as String;
      }
    }
    return http.StreamedResponse(
      Stream<List<int>>.value(bytes),
      response.statusCode,
      contentLength: bytes.length,
      request: response.request,
      headers: response.headers,
      reasonPhrase: response.reasonPhrase,
    );
  }

  @override
  void close() {
    _inner.close();
  }
}
