import 'dart:convert';

String unsignedJwt({required String role}) {
  String segment(Object value) {
    return base64Url.encode(utf8.encode(jsonEncode(value))).replaceAll('=', '');
  }

  return '${segment({'alg': 'none', 'typ': 'JWT'})}.${segment({'sub': 'user-1', 'role': role})}.sig';
}
