import 'package:flutter/material.dart';

import '../api/api_exception.dart';
import '../api/auth_api.dart';
import '../api/error_text.dart';
import '../auth/local_password.dart';
import '../auth/token_store.dart';
import '../i18n/locale_controller.dart';
import '../i18n/locale_toggle.dart';

class ForgotPasswordScreen extends StatefulWidget {
  const ForgotPasswordScreen({
    required this.locale,
    required this.api,
    required this.tokens,
    required this.onLeave,
    required this.onReset,
    super.key,
  });

  final LocaleController locale;
  final AuthApi api;
  final TokenStore tokens;
  final VoidCallback onLeave;
  final VoidCallback onReset;

  @override
  State<ForgotPasswordScreen> createState() => _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends State<ForgotPasswordScreen> {
  final TextEditingController _email = TextEditingController();
  final TextEditingController _code = TextEditingController();
  final TextEditingController _password = TextEditingController();
  final TextEditingController _confirm = TextEditingController();
  String? _error;
  bool _codeSent = false;
  bool _busy = false;

  @override
  void dispose() {
    _email.dispose();
    _code.dispose();
    _password.dispose();
    _confirm.dispose();
    super.dispose();
  }

  Future<void> _request() async {
    if (_busy) {
      return;
    }
    final email = _email.text.trim();
    if (email.isEmpty) {
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await widget.api.forgotPassword(email: email);
      if (!mounted) {
        return;
      }
      setState(() {
        _codeSent = true;
      });
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

  Future<void> _reset() async {
    if (_busy) {
      return;
    }
    final code = _code.text.trim();
    final password = _password.text;
    if (code.isEmpty || password.isEmpty) {
      return;
    }
    final issue = checkPassword(password, _confirm.text);
    if (issue != LocalPasswordIssue.none) {
      setState(() {
        _error = issue == LocalPasswordIssue.length
            ? widget.locale.messages.t('auth.passwordLength')
            : widget.locale.messages.t('auth.passwordMismatch');
      });
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await widget.api.resetPassword(code: code, password: password);
      await widget.tokens.clear();
      if (!mounted) {
        return;
      }
      widget.onReset();
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
      appBar: AppBar(
        title: Text(messages.t('auth.forgotTitle')),
        actions: [LocaleToggle(locale: widget.locale)],
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            TextField(
              key: const Key('email'),
              controller: _email,
              keyboardType: TextInputType.emailAddress,
              autocorrect: false,
              decoration: InputDecoration(labelText: messages.t('auth.email')),
              onSubmitted: (_) => _request(),
            ),
            const SizedBox(height: 24),
            FilledButton(
              key: const Key('forgot-submit'),
              onPressed: _busy ? null : _request,
              child: Text(messages.t('auth.forgotSubmit')),
            ),
            if (_codeSent) ...[
              const SizedBox(height: 16),
              Text(messages.t('auth.forgotSent'), key: const Key('forgot-message')),
              const SizedBox(height: 16),
              Text(messages.t('auth.resetTitle')),
              const SizedBox(height: 16),
              TextField(
                key: const Key('reset-code'),
                controller: _code,
                decoration: InputDecoration(labelText: messages.t('auth.code')),
                textInputAction: TextInputAction.next,
              ),
              const SizedBox(height: 16),
              TextField(
                key: const Key('password'),
                controller: _password,
                obscureText: true,
                decoration: InputDecoration(labelText: messages.t('auth.newPassword')),
                textInputAction: TextInputAction.next,
              ),
              const SizedBox(height: 16),
              TextField(
                key: const Key('confirm-password'),
                controller: _confirm,
                obscureText: true,
                decoration: InputDecoration(labelText: messages.t('auth.confirmPassword')),
                onSubmitted: (_) => _reset(),
              ),
              const SizedBox(height: 24),
              FilledButton(
                key: const Key('reset-submit'),
                onPressed: _busy ? null : _reset,
                child: Text(messages.t('auth.resetSubmit')),
              ),
            ],
            if (_error != null) ...[
              const SizedBox(height: 16),
              Text(_error!, key: const Key('forgot-error')),
            ],
            TextButton(
              key: const Key('leave-forgot'),
              onPressed: widget.onLeave,
              child: Text(messages.t('auth.goToLogin')),
            ),
          ],
        ),
      ),
    );
  }
}
