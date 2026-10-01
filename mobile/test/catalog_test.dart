import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/i18n/bn.dart';
import 'package:mobile/i18n/en.dart';
import 'package:mobile/i18n/locale_controller.dart';

void main() {
  test('English and Bangla catalogs have the same keys', () {
    expect(bangla.keys.toSet(), english.keys.toSet());
    for (final key in english.keys) {
      expect(english.t(key), isNotEmpty);
      expect(bangla.t(key), isNotEmpty);
    }
  });

  test('first launch uses English', () {
    final locale = LocaleController();
    expect(locale.code, 'en');
    expect(locale.messages.t('auth.loginTitle'), 'Sign in');
    locale.toggle();
    expect(locale.messages.t('auth.loginTitle'), 'সাইন ইন');
    locale.toggle();
    expect(locale.messages.t('auth.loginTitle'), 'Sign in');
  });
}
