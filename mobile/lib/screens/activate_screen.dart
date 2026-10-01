import 'package:flutter/material.dart';

import '../api/api_exception.dart';
import '../api/auth_api.dart';
import '../api/error_text.dart';
import '../auth/local_password.dart';
import '../auth/session.dart';
import '../auth/token_store.dart';
import '../i18n/locale_controller.dart';
import '../i18n/locale_toggle.dart';

class ActivateScreen extends StatefulWidget {
  const ActivateScreen({
    required this.locale,
    required this.api,
    required this.tokens,
    required this.onSignedIn,
    required this.onLeave,
    super.key,
  });

  final LocaleController locale;
  final AuthApi api;
  final TokenStore tokens;
  final VoidCallback onSignedIn;
  final VoidCallback onLeave;

  @override
  State<ActivateScreen> createState() => _ActivateScreenState();
}

class _ActivateScreenState extends State<ActivateScreen> {
  final TextEditingController _phone = TextEditingController();
  final TextEditingController _code = TextEditingController();
  final TextEditingController _password = TextEditingController();
  final TextEditingController _confirm = TextEditingController();
  String? _error;
  bool _busy = false;

  @override
  void dispose() {
    _phone.dispose();
    _code.dispose();
    _password.dispose();
    _confirm.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy) {
      return;
    }
    final phone = _phone.text.trim();
    final code = _code.text.trim();
    final password = _password.text;
    if (phone.isEmpty || code.isEmpty || password.isEmpty) {
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
      await widget.api.activate(phone: phone, code: code, password: password);
      final tokens = await widget.api.login(identifier: phone, password: password);
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
      appBar: AppBar(title: Text(messages.t('auth.activateTitle')), actions: [LocaleToggle(locale: widget.locale)]),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            TextField(
              key: const Key('phone'),
              controller: _phone,
              keyboardType: TextInputType.phone,
              decoration: InputDecoration(labelText: messages.t('auth.phone')),
              textInputAction: TextInputAction.next,
            ),
            const SizedBox(height: 16),
            TextField(
              key: const Key('activation-code'),
              controller: _code,
              decoration: InputDecoration(labelText: messages.t('auth.activationCode')),
              textInputAction: TextInputAction.next,
            ),
            const SizedBox(height: 16),
            TextField(
              key: const Key('password'),
              controller: _password,
              obscureText: true,
              decoration: InputDecoration(labelText: messages.t('auth.password')),
              textInputAction: TextInputAction.next,
            ),
            const SizedBox(height: 16),
            TextField(
              key: const Key('confirm-password'),
              controller: _confirm,
              obscureText: true,
              decoration: InputDecoration(labelText: messages.t('auth.confirmPassword')),
              onSubmitted: (_) => _submit(),
            ),
            if (_error != null) ...[
              const SizedBox(height: 16),
              Text(_error!, key: const Key('activate-error')),
            ],
            const SizedBox(height: 24),
            FilledButton(
              key: const Key('activate-submit'),
              onPressed: _busy ? null : _submit,
              child: Text(messages.t('auth.activateSubmit')),
            ),
            TextButton(
              key: const Key('leave-activate'),
              onPressed: widget.onLeave,
              child: Text(messages.t('auth.goToLogin')),
            ),
          ],
        ),
      ),
    );
  }
}
