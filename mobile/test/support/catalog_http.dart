import 'dart:convert';

import 'package:http/http.dart' as http;

/// Empty catalog list for widget tests that open the home screen.
http.Response? tryCatalogList(http.Request request) {
  if (request.url.path.endsWith('/catalog/courses') ||
      request.url.path.endsWith('/catalog/books') ||
      request.url.path.endsWith('/catalog/course-categories') ||
      request.url.path.endsWith('/catalog/book-categories')) {
    if (request.url.path.endsWith('/catalog/courses') || request.url.path.endsWith('/catalog/books')) {
      return catalogPage([]);
    }
    return http.Response('[]', 200, headers: {'content-type': 'application/json'});
  }
  return null;
}

http.Response catalogPage(List<Map<String, dynamic>> content) {
  return http.Response(
    jsonEncode({
      'content': content,
      'page': 0,
      'size': 20,
      'totalElements': content.length,
      'totalPages': content.isEmpty ? 0 : 1,
    }),
    200,
    headers: {'content-type': 'application/json'},
  );
}

http.Response catalogError() {
  return http.Response(
    jsonEncode({
      'code': 'INTERNAL_ERROR',
      'message': 'fail',
      'timestamp': '2026-10-02T00:00:00Z',
      'requestId': 'req-1',
    }),
    500,
    headers: {'content-type': 'application/json'},
  );
}
