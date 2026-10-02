class ApiException implements Exception {
  const ApiException({required this.code, required this.message, this.requestId});

  final String code;
  final String message;
  final String? requestId;

  @override
  String toString() => 'ApiException($code)';
}
