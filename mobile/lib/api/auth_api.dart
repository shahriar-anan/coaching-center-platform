import 'dart:convert';

import 'package:http/http.dart' as http;

import '../auth/jwt_role.dart';
import '../auth/token_store.dart';
import 'api_exception.dart';

class TokenPair {
  const TokenPair({required this.accessToken, required this.refreshToken, required this.expiresInSeconds});

  final String accessToken;
  final String refreshToken;
  final int expiresInSeconds;
}

class MeProfile {
  const MeProfile({required this.fullName});

  final String fullName;
}

class AuthApi {
  AuthApi({required this.baseUrl, http.Client? client}) : _client = client ?? http.Client();

  final String baseUrl;
  final http.Client _client;

  TokenStore? _tokens;
  void Function()? _onSessionCleared;

  /// Session storage used by the 401 refresh-and-retry path.
  void bind({required TokenStore tokens, required void Function() onSessionCleared}) {
    _tokens = tokens;
    _onSessionCleared = onSessionCleared;
  }

  /// Login for `X-Client-Type: mobile`. The refresh token is read only from
  /// the JSON body. A refresh cookie is rejected.
  Future<TokenPair> login({required String identifier, required String password}) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/api/v1/auth/login'),
      headers: _mobileJsonHeaders,
      body: jsonEncode({'identifier': identifier, 'password': password}),
    );
    return _tokenPair(response);
  }

  /// Refresh for `X-Client-Type: mobile`. The presented refresh token is sent
  /// in the JSON body. A refresh cookie is rejected.
  Future<TokenPair> refresh({required String refreshToken}) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/api/v1/auth/refresh'),
      headers: _mobileJsonHeaders,
      body: jsonEncode({'refreshToken': refreshToken}),
    );
    return _tokenPair(response);
  }

  Future<void> logout({required String accessToken, required String refreshToken}) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/api/v1/auth/logout'),
      headers: {..._mobileJsonHeaders, 'Authorization': 'Bearer $accessToken'},
      body: jsonEncode({'refreshToken': refreshToken}),
    );
    _rejectRefreshCookie(response);
    if (response.statusCode != 204) {
      throw _exceptionFromBody(response.body);
    }
  }

  Future<void> register({
    required String fullName,
    required String phone,
    required String email,
    required String password,
  }) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/api/v1/auth/register'),
      headers: _jsonHeaders,
      body: jsonEncode({'fullName': fullName, 'phone': phone, 'email': email, 'password': password}),
    );
    if (response.statusCode != 201) {
      throw _exceptionFromBody(response.body);
    }
  }

  Future<void> verifyEmail({required String code}) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/api/v1/auth/verify-email'),
      headers: _jsonHeaders,
      body: jsonEncode({'code': code}),
    );
    if (response.statusCode != 204) {
      throw _exceptionFromBody(response.body);
    }
  }

  Future<void> resendEmailVerification({required String email}) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/api/v1/auth/verify-email/resend'),
      headers: _jsonHeaders,
      body: jsonEncode({'email': email}),
    );
    if (response.statusCode != 200) {
      throw _exceptionFromBody(response.body);
    }
  }

  Future<void> activate({required String phone, required String code, required String password}) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/api/v1/auth/activate'),
      headers: _jsonHeaders,
      body: jsonEncode({'phone': phone, 'code': code, 'password': password}),
    );
    if (response.statusCode != 204) {
      throw _exceptionFromBody(response.body);
    }
  }

  Future<void> forgotPassword({required String email}) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/api/v1/auth/forgot-password'),
      headers: _jsonHeaders,
      body: jsonEncode({'email': email}),
    );
    if (response.statusCode != 200) {
      throw _exceptionFromBody(response.body);
    }
  }

  Future<void> resetPassword({required String code, required String password}) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/api/v1/auth/reset-password'),
      headers: _jsonHeaders,
      body: jsonEncode({'code': code, 'password': password}),
    );
    if (response.statusCode != 204) {
      throw _exceptionFromBody(response.body);
    }
  }

  /// Current user. On 401, refreshes once and retries once. A second 401 clears
  /// both tokens and notifies the session.
  Future<MeProfile> me() async {
    final response = await _withRefreshRetry(() async {
      final access = await _tokens?.readAccessToken();
      return _client.get(
        Uri.parse('$baseUrl/api/v1/me'),
        headers: {'Accept': 'application/json', 'Authorization': 'Bearer ${access ?? ''}'},
      );
    });
    if (response.statusCode != 200) {
      throw _exceptionFromBody(response.body);
    }
    final decoded = jsonDecode(response.body);
    if (decoded is! Map || decoded['fullName'] is! String || (decoded['fullName'] as String).isEmpty) {
      throw const ApiException(code: 'INTERNAL_ERROR', message: 'Unexpected response');
    }
    return MeProfile(fullName: decoded['fullName'] as String);
  }

  Future<http.Response> _withRefreshRetry(Future<http.Response> Function() send) async {
    final first = await send();
    if (first.statusCode != 401) {
      return first;
    }
    final refreshed = await _refreshStored();
    if (!refreshed) {
      await _expire();
      return first;
    }
    final second = await send();
    if (second.statusCode == 401) {
      await _expire();
    }
    return second;
  }

  Future<bool> _refreshStored() async {
    final store = _tokens;
    if (store == null) {
      return false;
    }
    final refreshToken = await store.readRefreshToken();
    if (refreshToken == null || refreshToken.isEmpty) {
      return false;
    }
    try {
      final pair = await refresh(refreshToken: refreshToken);
      if (roleFromAccessToken(pair.accessToken) != 'STUDENT') {
        return false;
      }
      await store.writeAccessToken(pair.accessToken);
      await store.writeRefreshToken(pair.refreshToken);
      return true;
    } on ApiException {
      return false;
    }
  }

  Future<void> _expire() async {
    await _tokens?.clear();
    _onSessionCleared?.call();
  }

  TokenPair _tokenPair(http.Response response) {
    _rejectRefreshCookie(response);
    if (response.statusCode != 200) {
      throw _exceptionFromBody(response.body);
    }
    final decoded = jsonDecode(response.body);
    if (decoded is! Map) {
      throw const ApiException(code: 'INTERNAL_ERROR', message: 'Unexpected response');
    }
    final accessToken = decoded['accessToken'];
    final refreshToken = decoded['refreshToken'];
    final expiresInSeconds = decoded['expiresInSeconds'];
    if (accessToken is! String ||
        accessToken.isEmpty ||
        refreshToken is! String ||
        refreshToken.isEmpty ||
        expiresInSeconds is! int) {
      throw const ApiException(code: 'INTERNAL_ERROR', message: 'Unexpected response');
    }
    return TokenPair(accessToken: accessToken, refreshToken: refreshToken, expiresInSeconds: expiresInSeconds);
  }
}

const _jsonHeaders = {'Content-Type': 'application/json', 'Accept': 'application/json'};

const _mobileJsonHeaders = {..._jsonHeaders, 'X-Client-Type': 'mobile'};

void _rejectRefreshCookie(http.Response response) {
  if (_setsRefreshCookie(response)) {
    throw const ApiException(code: 'INTERNAL_ERROR', message: 'Unexpected response');
  }
}

bool _setsRefreshCookie(http.Response response) {
  for (final entry in response.headers.entries) {
    if (entry.key.toLowerCase() == 'set-cookie' && entry.value.toLowerCase().contains('refresh')) {
      return true;
    }
  }
  return false;
}

ApiException _exceptionFromBody(String body) {
  try {
    final decoded = jsonDecode(body);
    if (decoded is Map && decoded['code'] is String) {
      return ApiException(
        code: decoded['code'] as String,
        message: decoded['message'] is String ? decoded['message'] as String : '',
        requestId: decoded['requestId'] is String ? decoded['requestId'] as String : null,
      );
    }
  } on FormatException {
    // Fall through to the generic code.
  }
  return const ApiException(code: 'INTERNAL_ERROR', message: '');
}
