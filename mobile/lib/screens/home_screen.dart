import 'package:flutter/material.dart';

import '../api/api_exception.dart';
import '../api/auth_api.dart';
import '../api/error_text.dart';
import '../i18n/locale_controller.dart';
import '../i18n/locale_toggle.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({required this.locale, required this.api, required this.onLogout, super.key});

  final LocaleController locale;
  final AuthApi api;
  final Future<void> Function() onLogout;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  String? _name;
  String? _error;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final me = await widget.api.me();
      if (!mounted) {
        return;
      }
      setState(() {
        _name = me.fullName;
        _loading = false;
      });
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _error = widget.locale.messages.t(errorMessageKey(error.code));
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final messages = widget.locale.messages;
    return Scaffold(
      key: const Key('home'),
      appBar: AppBar(
        title: Text(messages.t('home.title')),
        actions: [
          LocaleToggle(locale: widget.locale),
          TextButton(
            key: const Key('logout'),
            onPressed: () => widget.onLogout(),
            child: Text(messages.t('auth.logout')),
          ),
        ],
      ),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: _loading
              ? Text(messages.t('home.loading'))
              : Text(_error ?? _name ?? '', key: const Key('home-name'), textAlign: TextAlign.center),
        ),
      ),
    );
  }
}
