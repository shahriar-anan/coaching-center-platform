import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/files/safe_file_name.dart';

void main() {
  test('sanitize keeps Bangla characters', () {
    expect(sanitizeDownloadLeafName('নোট.pdf'), 'নোট.pdf');
  });

  test('sanitize removes path separators only', () {
    expect(sanitizeDownloadLeafName(r'foo\bar.pdf'), 'foo_bar.pdf');
  });

  test('fallback supplies extension when leaf is empty', () {
    expect(fallbackLeafName('application/pdf'), 'file.pdf');
  });
}
