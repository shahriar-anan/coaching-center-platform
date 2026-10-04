import 'dart:convert';

import 'package:http/http.dart' as http;

import '../auth/jwt_role.dart';
import '../auth/token_store.dart';
import 'api_exception.dart';
import 'catalog_models.dart';

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

  Future<CatalogPage<CourseSummary>> listCourses({String q = '', String? categoryId}) async {
    final params = <String, String>{'q': q};
    if (categoryId != null && categoryId.isNotEmpty) {
      params['categoryId'] = categoryId;
    }
    final uri = Uri.parse('$baseUrl/api/v1/catalog/courses').replace(queryParameters: params);
    final response = await _authorizedGet(uri);
    return _catalogPage(response, CourseSummary.fromJson);
  }

  Future<CatalogPage<BookSummary>> listBooks({String q = '', String? categoryId}) async {
    final params = <String, String>{'q': q};
    if (categoryId != null && categoryId.isNotEmpty) {
      params['categoryId'] = categoryId;
    }
    final uri = Uri.parse('$baseUrl/api/v1/catalog/books').replace(queryParameters: params);
    final response = await _authorizedGet(uri);
    return _catalogPage(response, BookSummary.fromJson);
  }

  Future<List<CourseCategoryNode>> listCourseCategories() async {
    final uri = Uri.parse('$baseUrl/api/v1/catalog/course-categories');
    final response = await _authorizedGet(uri);
    final decoded = jsonDecode(response.body);
    if (decoded is! List) {
      throw const ApiException(code: 'INTERNAL_ERROR', message: 'Unexpected response');
    }
    return decoded.map((entry) => CourseCategoryNode.fromJson(Map<String, dynamic>.from(entry as Map))).toList();
  }

  Future<List<BookCategorySummary>> listBookCategories() async {
    final uri = Uri.parse('$baseUrl/api/v1/catalog/book-categories');
    final response = await _authorizedGet(uri);
    final decoded = jsonDecode(response.body);
    if (decoded is! List) {
      throw const ApiException(code: 'INTERNAL_ERROR', message: 'Unexpected response');
    }
    return decoded.map((entry) => BookCategorySummary.fromJson(Map<String, dynamic>.from(entry as Map))).toList();
  }

  Future<CourseDetail> getCourse(String courseId) async {
    final uri = Uri.parse('$baseUrl/api/v1/catalog/courses/$courseId');
    final response = await _authorizedGet(uri);
    return CourseDetail.fromJson(Map<String, dynamic>.from(jsonDecode(response.body) as Map));
  }

  Future<List<MyCourseSummary>> listMyCourses() async {
    final uri = Uri.parse('$baseUrl/api/v1/me/courses');
    final response = await _authorizedGet(uri);
    final decoded = jsonDecode(response.body);
    if (decoded is! List) {
      throw const ApiException(code: 'INTERNAL_ERROR', message: 'Unexpected response');
    }
    return decoded.map((entry) => MyCourseSummary.fromJson(Map<String, dynamic>.from(entry as Map))).toList();
  }

  Future<LectureContent> getLecture(String courseId, String lectureId) async {
    final uri = Uri.parse('$baseUrl/api/v1/catalog/courses/$courseId/lectures/$lectureId');
    final response = await _authorizedJsonGet(uri);
    if (response.statusCode == 200) {
      return LectureContent.fromJson(Map<String, dynamic>.from(jsonDecode(response.body) as Map));
    }
    throw _exceptionFromBody(response.body);
  }

  Future<CoursePurchaseResult> purchaseCourse({
    required String courseId,
    required String method,
    required String transactionRef,
    String? deliveryMode,
  }) async {
    final body = <String, dynamic>{'method': method, 'transactionRef': transactionRef};
    if (deliveryMode != null) {
      body['deliveryMode'] = deliveryMode;
    }
    final uri = Uri.parse('$baseUrl/api/v1/courses/$courseId/purchases');
    final response = await _authorizedJsonPost(uri, body);
    if (response.statusCode != 201) {
      throw _exceptionFromBody(response.body);
    }
    final decoded = jsonDecode(response.body);
    if (decoded is! Map) {
      throw const ApiException(code: 'INTERNAL_ERROR', message: 'Unexpected response');
    }
    return CoursePurchaseResult.fromJson(Map<String, dynamic>.from(decoded));
  }

  Future<BookDetail> getBook(String bookId) async {
    final uri = Uri.parse('$baseUrl/api/v1/catalog/books/$bookId');
    final response = await _authorizedGet(uri);
    return BookDetail.fromJson(Map<String, dynamic>.from(jsonDecode(response.body) as Map));
  }

  Future<BookOrderResult> placeBookOrder({
    required String bookId,
    required String fulfillment,
    required String paymentMethod,
    required String phone,
    String? address,
    String? area,
    String? method,
    String? transactionRef,
  }) async {
    final body = <String, dynamic>{
      'fulfillment': fulfillment,
      'paymentMethod': paymentMethod,
      'phone': phone,
    };
    if (address != null && address.isNotEmpty) {
      body['address'] = address;
    }
    if (area != null && area.isNotEmpty) {
      body['area'] = area;
    }
    if (method != null) {
      body['method'] = method;
    }
    if (transactionRef != null) {
      body['transactionRef'] = transactionRef;
    }
    final uri = Uri.parse('$baseUrl/api/v1/books/$bookId/orders');
    final response = await _authorizedJsonPost(uri, body);
    if (response.statusCode != 201) {
      throw _exceptionFromBody(response.body);
    }
    final decoded = jsonDecode(response.body);
    if (decoded is! Map) {
      throw const ApiException(code: 'INTERNAL_ERROR', message: 'Unexpected response');
    }
    return BookOrderResult.fromJson(Map<String, dynamic>.from(decoded));
  }

  Future<List<BookOrderView>> listMyOrders() async {
    final uri = Uri.parse('$baseUrl/api/v1/me/orders');
    final response = await _authorizedGet(uri);
    final decoded = jsonDecode(response.body);
    if (decoded is! List) {
      throw const ApiException(code: 'INTERNAL_ERROR', message: 'Unexpected response');
    }
    return decoded.map((entry) => BookOrderView.fromJson(Map<String, dynamic>.from(entry as Map))).toList();
  }

  Future<List<CoursePurchaseView>> listMyPurchases() async {
    final uri = Uri.parse('$baseUrl/api/v1/me/purchases');
    final response = await _authorizedGet(uri);
    final decoded = jsonDecode(response.body);
    if (decoded is! List) {
      throw const ApiException(code: 'INTERNAL_ERROR', message: 'Unexpected response');
    }
    return decoded
        .map((entry) => CoursePurchaseView.fromJson(Map<String, dynamic>.from(entry as Map)))
        .toList();
  }

  Future<FileDownload> downloadFile(String fileId) async {
    final uri = Uri.parse('$baseUrl/api/v1/files/$fileId');
    final response = await _authorizedBytesGet(uri);
    if (response.statusCode != 200) {
      throw _exceptionFromBody(response.body);
    }
    final disposition = response.headers['content-disposition'] ?? '';
    final match = RegExp(r'filename="([^"]+)"').firstMatch(disposition);
    final fileName = match?.group(1) ?? 'download';
    final contentType = response.headers['content-type'] ?? 'application/octet-stream';
    return FileDownload(bytes: response.bodyBytes, contentType: contentType, fileName: fileName);
  }

  Future<http.Response> _authorizedJsonPost(Uri uri, Map<String, dynamic> body) async {
    return _withRefreshRetry(() async {
      final access = await _tokens?.readAccessToken();
      return _client.post(
        uri,
        headers: {'Content-Type': 'application/json', 'Accept': 'application/json', 'Authorization': 'Bearer ${access ?? ''}'},
        body: jsonEncode(body),
      );
    });
  }

  Future<http.Response> _authorizedGet(Uri uri) async {
    final response = await _withRefreshRetry(() async {
      final access = await _tokens?.readAccessToken();
      return _client.get(
        uri,
        headers: {'Accept': 'application/json', 'Authorization': 'Bearer ${access ?? ''}'},
      );
    });
    if (response.statusCode != 200) {
      throw _exceptionFromBody(response.body);
    }
    return response;
  }

  Future<http.Response> _authorizedJsonGet(Uri uri) async {
    return _withRefreshRetry(() async {
      final access = await _tokens?.readAccessToken();
      return _client.get(
        uri,
        headers: {'Accept': 'application/json', 'Authorization': 'Bearer ${access ?? ''}'},
      );
    });
  }

  Future<http.Response> _authorizedBytesGet(Uri uri) async {
    final response = await _withRefreshRetry(() async {
      final access = await _tokens?.readAccessToken();
      return _client.get(
        uri,
        headers: {'Authorization': 'Bearer ${access ?? ''}'},
      );
    });
    return response;
  }

  CatalogPage<T> _catalogPage<T>(http.Response response, T Function(Map<String, dynamic>) mapItem) {
    final decoded = jsonDecode(response.body);
    if (decoded is! Map || decoded['content'] is! List) {
      throw const ApiException(code: 'INTERNAL_ERROR', message: 'Unexpected response');
    }
    final items = (decoded['content'] as List)
        .map((entry) => mapItem(Map<String, dynamic>.from(entry as Map)))
        .toList();
    return CatalogPage(content: items);
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
