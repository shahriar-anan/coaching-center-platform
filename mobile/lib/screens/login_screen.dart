import 'package:flutter/material.dart';

import '../api/api_exception.dart';
import '../api/auth_api.dart';
import '../api/error_text.dart';
import '../auth/session.dart';
import '../auth/token_store.dart';
import '../i18n/locale_controller.dart';
import '../i18n/locale_toggle.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({
    required this.locale,
    required this.api,
    required this.tokens,
    required this.onSignedIn,
    required this.onRegister,
    required this.onActivate,
    required this.onForgot,
    this.notice,
    super.key,
  });

  final LocaleController locale;
  final AuthApi api;
  final TokenStore tokens;
  final VoidCallback onSignedIn;
  final VoidCallback onRegister;
  final VoidCallback onActivate;
  final VoidCallback onForgot;
  final String? notice;

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final TextEditingController _identifier = TextEditingController();
  final TextEditingController _password = TextEditingController();
  String? _error;
  bool _busy = false;

  @override
  void dispose() {
    _identifier.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy) {
      return;
    }
    final identifier = _identifier.text.trim();
    final password = _password.text;
    if (identifier.isEmpty || password.isEmpty) {
      return;
    }

    setState(() {
      _busy = true;
      _error = null;
    });

    try {
      final tokens = await widget.api.login(identifier: identifier, password: password);
      final gate = await completeLogin(widget.tokens, tokens);
      if (!mounted) {
        return;
      }
      if (gate != LoginGate.student) {
        setState(() {
          _error = widget.locale.messages.t('auth.studentsOnly');
        });
        return;
      }
      widget.onSignedIn();
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _error = widget.locale.messages.t(errorMessageKey(error.code));
      });
    } finally {
      if (mounted) {
        setState(() {
          _busy = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final messages = widget.locale.messages;
    return Scaffold(
      appBar: AppBar(title: Text(messages.t('auth.loginTitle')), actions: [LocaleToggle(locale: widget.locale)]),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            if (widget.notice != null) ...[
              Text(widget.notice!, key: const Key('login-notice')),
              const SizedBox(height: 16),
            ],
            TextField(
              key: const Key('identifier'),
              controller: _identifier,
              keyboardType: TextInputType.emailAddress,
              autocorrect: false,
              decoration: InputDecoration(labelText: messages.t('auth.identifier')),
              textInputAction: TextInputAction.next,
            ),
            const SizedBox(height: 16),
            TextField(
              key: const Key('password'),
              controller: _password,
              obscureText: true,
              decoration: InputDecoration(labelText: messages.t('auth.password')),
              onSubmitted: (_) => _submit(),
            ),
            if (_error != null) ...[
              const SizedBox(height: 16),
              Text(_error!, key: const Key('login-error')),
            ],
            const SizedBox(height: 24),
            FilledButton(
              key: const Key('login-submit'),
              onPressed: _busy ? null : _submit,
              child: _busy
                  ? const SizedBox.square(dimension: 18, child: CircularProgressIndicator(strokeWidth: 2))
                  : Text(messages.t('auth.submit')),
            ),
            const SizedBox(height: 8),
            TextButton(
              key: const Key('go-register'),
              onPressed: widget.onRegister,
              child: Text(messages.t('auth.register')),
            ),
            TextButton(
              key: const Key('go-activate'),
              onPressed: widget.onActivate,
              child: Text(messages.t('auth.activate')),
            ),
            TextButton(
              key: const Key('go-forgot'),
              onPressed: widget.onForgot,
              child: Text(messages.t('auth.forgot')),
            ),
          ],
        ),
      ),
    );
  }
}
