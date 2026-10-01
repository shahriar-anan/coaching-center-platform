import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:mobile/api/api_exception.dart';
import 'package:mobile/api/auth_api.dart';
import 'package:mobile/api/error_text.dart';
import 'package:mobile/config/api_base_url.dart';

void main() {
  const baseUrl = 'http://10.0.2.2:8080';

  test('default base URL is the emulator host', () {
    expect(apiBaseUrl(), defaultApiBaseUrl);
    expect(normalizeBaseUrl('http://10.0.2.2:8080/'), 'http://10.0.2.2:8080');
  });

  test('login posts identifier and password with the mobile client header', () async {
    late http.Request captured;
    final client = MockClient((request) async {
      captured = request;
      return http.Response(
        jsonEncode({
          'accessToken': 'access-1',
          'expiresInSeconds': 900,
          'refreshToken': 'refresh-1',
        }),
        200,
        headers: {'content-type': 'application/json'},
      );
    });
    final api = AuthApi(baseUrl: baseUrl, client: client);

    final tokens = await api.login(identifier: 'user@example.com', password: 'password1');

    expect(captured.method, 'POST');
    expect(captured.url.toString(), '$baseUrl/api/v1/auth/login');
    expect(captured.headers['x-client-type'], 'mobile');
    expect(captured.headers['content-type'], contains('application/json'));
    expect(captured.headers['cookie'], isNull);
    expect(jsonDecode(captured.body), {'identifier': 'user@example.com', 'password': 'password1'});
    expect(tokens.accessToken, 'access-1');
    expect(tokens.refreshToken, 'refresh-1');
    expect(tokens.expiresInSeconds, 900);
  });

  test('login rejects a refresh cookie and a missing refresh token', () async {
    final cookieClient = MockClient((request) async {
      return http.Response(
        jsonEncode({
          'accessToken': 'access-1',
          'expiresInSeconds': 900,
          'refreshToken': 'refresh-1',
        }),
        200,
        headers: {'set-cookie': 'refresh_token=secret; HttpOnly; Path=/api/v1/auth'},
      );
    });
    final cookieApi = AuthApi(baseUrl: baseUrl, client: cookieClient);
    expect(
      () => cookieApi.login(identifier: 'user@example.com', password: 'password1'),
      throwsA(isA<ApiException>().having((error) => error.code, 'code', 'INTERNAL_ERROR')),
    );

    final missingClient = MockClient((request) async {
      return http.Response(
        jsonEncode({'accessToken': 'access-1', 'expiresInSeconds': 900}),
        200,
        headers: {'content-type': 'application/json'},
      );
    });
    final missingApi = AuthApi(baseUrl: baseUrl, client: missingClient);
    expect(
      () => missingApi.login(identifier: 'user@example.com', password: 'password1'),
      throwsA(isA<ApiException>()),
    );
  });

  test('invalid credentials keep the API code for catalog mapping', () async {
    final client = MockClient((request) async {
      return http.Response(
        jsonEncode({
          'code': 'INVALID_CREDENTIALS',
          'message': 'Invalid credentials.',
          'timestamp': '2026-09-26T00:00:00Z',
          'requestId': 'req-1',
        }),
        401,
        headers: {'content-type': 'application/json'},
      );
    });
    final api = AuthApi(baseUrl: baseUrl, client: client);

    await expectLater(
      api.login(identifier: 'user@example.com', password: 'wrong'),
      throwsA(
        isA<ApiException>()
            .having((error) => error.code, 'code', 'INVALID_CREDENTIALS')
            .having((error) => error.requestId, 'requestId', 'req-1'),
      ),
    );
    expect(errorMessageKey('INVALID_CREDENTIALS'), 'errors.invalidCredentials');
  });
}
