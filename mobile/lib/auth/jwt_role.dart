import 'dart:convert';

/// Reads the `role` claim from an access token payload. The signature is not
/// verified here; the API issued the token.
String? roleFromAccessToken(String token) {
  final parts = token.split('.');
  if (parts.length < 2 || parts[1].isEmpty) {
    return null;
  }
  try {
    var payload = parts[1];
    final remainder = payload.length % 4;
    if (remainder > 0) {
      payload += '=' * (4 - remainder);
    }
    final decoded = jsonDecode(utf8.decode(base64Url.decode(payload)));
    if (decoded is Map && decoded['role'] is String) {
      return decoded['role'] as String;
    }
  } on FormatException {
    return null;
  } on ArgumentError {
    return null;
  }
  return null;
}
