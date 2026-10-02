import 'package:flutter/material.dart';

import 'locale_controller.dart';

class LocaleToggle extends StatelessWidget {
  const LocaleToggle({required this.locale, super.key});

  final LocaleController locale;

  @override
  Widget build(BuildContext context) {
    final messages = locale.messages;
    final label = locale.code == 'en' ? messages.t('locale.bn') : messages.t('locale.en');
    return TextButton(
      key: const Key('locale-toggle'),
      onPressed: locale.toggle,
      child: Text(label),
    );
  }
}
