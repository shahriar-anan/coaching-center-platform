const defaultApiBaseUrl = 'http://10.0.2.2:8080';

const _flavor = String.fromEnvironment('FLAVOR', defaultValue: 'dev');
const _definedApiBaseUrl = String.fromEnvironment('API_BASE_URL');

/// Strips a trailing slash so request paths concatenate cleanly.
String normalizeBaseUrl(String raw) {
  final trimmed = raw.trim();
  if (trimmed.endsWith('/')) {
    return trimmed.substring(0, trimmed.length - 1);
  }
  return trimmed;
}

/// Resolves the API host for a flavor.
///
/// `dev` defaults to [defaultApiBaseUrl]. `prod` has no built-in host.
/// A non-empty [definedUrl] (`--dart-define=API_BASE_URL=`) wins for either flavor.
String resolveApiBaseUrl({required String flavor, required String definedUrl}) {
  if (definedUrl.trim().isNotEmpty) {
    return normalizeBaseUrl(definedUrl);
  }
  if (flavor == 'prod') {
    return '';
  }
  return defaultApiBaseUrl;
}

/// Base URL for this build. Override on a phone with
/// `--dart-define=API_BASE_URL=http://<lan-ip>:8080`.
String apiBaseUrl() => resolveApiBaseUrl(flavor: _flavor, definedUrl: _definedApiBaseUrl);
