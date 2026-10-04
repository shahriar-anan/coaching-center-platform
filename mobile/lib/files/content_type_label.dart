String contentTypeLabel(String contentType) {
  final value = contentType.trim().toLowerCase();
  if (value.isEmpty) {
    return '';
  }
  if (value.contains('pdf')) {
    return 'PDF';
  }
  if (value.startsWith('image/')) {
    return value.substring('image/'.length).toUpperCase();
  }
  final slash = value.indexOf('/');
  if (slash > 0 && slash < value.length - 1) {
    return value.substring(slash + 1).toUpperCase();
  }
  return value.toUpperCase();
}
