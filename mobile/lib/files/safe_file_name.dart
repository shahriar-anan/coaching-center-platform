String sanitizeDownloadLeafName(String originalFileName) {
  var name = originalFileName.replaceAll(RegExp(r'[\x00-\x1f]'), '');
  name = name.replaceAll(RegExp(r'[\\/:*?"<>|]'), '_');
  while (name.contains('..')) {
    name = name.replaceAll('..', '_');
  }
  name = name.trim();
  if (name.isEmpty || name == '.' || name == '..') {
    return '';
  }
  return name;
}

String fallbackLeafName(String contentType) {
  if (contentType.toLowerCase() == 'application/pdf') {
    return 'file.pdf';
  }
  if (contentType.toLowerCase().startsWith('image/')) {
    final subtype = contentType.split('/').last;
    return 'file.$subtype';
  }
  return 'file.bin';
}
