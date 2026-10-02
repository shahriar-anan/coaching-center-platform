import 'package:flutter/material.dart';

import '../api/api_exception.dart';
import '../api/auth_api.dart';
import '../api/error_text.dart';
import '../auth/local_password.dart';
import '../i18n/locale_controller.dart';
import '../i18n/locale_toggle.dart';

class RegisterScreen extends StatefulWidget {
  const RegisterScreen({required this.locale, required this.api, required this.onLeave, super.key});

  final LocaleController locale;
  final AuthApi api;
  final VoidCallback onLeave;

  @override
  State<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends State<RegisterScreen> {
  final TextEditingController _fullName = TextEditingController();
  final TextEditingController _phone = TextEditingController();
  final TextEditingController _email = TextEditingController();
  final TextEditingController _password = TextEditingController();
  final TextEditingController _confirm = TextEditingController();
  final TextEditingController _code = TextEditingController();
  String? _error;
  String? _info;
  bool _busy = false;
  bool _verify = false;

  @override
  void dispose() {
    _fullName.dispose();
    _phone.dispose();
    _email.dispose();
    _password.dispose();
    _confirm.dispose();
    _code.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy) {
      return;
    }
    final fullName = _fullName.text.trim();
    final phone = _phone.text.trim();
    final email = _email.text.trim();
    final password = _password.text;
    if (fullName.isEmpty || phone.isEmpty || email.isEmpty || password.isEmpty) {
      return;
    }
    final issue = checkPassword(password, _confirm.text);
    if (issue != LocalPasswordIssue.none) {
      setState(() {
        _error = _passwordMessage(issue);
      });
      return;
    }

    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await widget.api.register(fullName: fullName, phone: phone, email: email, password: password);
      if (!mounted) {
        return;
      }
      setState(() {
        _verify = true;
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

  Future<void> _verifyCode() async {
    if (_busy) {
      return;
    }
    final code = _code.text.trim();
    if (code.isEmpty) {
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await widget.api.verifyEmail(code: code);
      if (!mounted) {
        return;
      }
      setState(() {
        _info = widget.locale.messages.t('auth.verified');
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

  Future<void> _resend() async {
    if (_busy) {
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await widget.api.resendEmailVerification(email: _email.text.trim());
      if (!mounted) {
        return;
      }
      setState(() {
        _info = widget.locale.messages.t('auth.resendSent');
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

  String _passwordMessage(LocalPasswordIssue issue) {
    final messages = widget.locale.messages;
    return issue == LocalPasswordIssue.length ? messages.t('auth.passwordLength') : messages.t('auth.passwordMismatch');
  }

  @override
  Widget build(BuildContext context) {
    final messages = widget.locale.messages;
    return Scaffold(
      appBar: AppBar(
        title: Text(messages.t(_verify ? 'auth.verifyTitle' : 'auth.registerTitle')),
        actions: [LocaleToggle(locale: widget.locale)],
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            if (!_verify) ...[
              TextField(
                key: const Key('full-name'),
                controller: _fullName,
                decoration: InputDecoration(labelText: messages.t('auth.fullName')),
                textInputAction: TextInputAction.next,
              ),
              const SizedBox(height: 16),
              TextField(
                key: const Key('phone'),
                controller: _phone,
                keyboardType: TextInputType.phone,
                decoration: InputDecoration(labelText: messages.t('auth.phone')),
                textInputAction: TextInputAction.next,
              ),
              const SizedBox(height: 16),
              TextField(
                key: const Key('email'),
                controller: _email,
                keyboardType: TextInputType.emailAddress,
                autocorrect: false,
                decoration: InputDecoration(labelText: messages.t('auth.email')),
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
            ] else ...[
              Text(messages.t('auth.verifyHint')),
              const SizedBox(height: 16),
              TextField(
                key: const Key('verification-code'),
                controller: _code,
                decoration: InputDecoration(labelText: messages.t('auth.code')),
              ),
              const SizedBox(height: 16),
              FilledButton(
                key: const Key('verify-submit'),
                onPressed: _busy ? null : _verifyCode,
                child: Text(messages.t('auth.verifySubmit')),
              ),
              TextButton(
                key: const Key('resend'),
                onPressed: _busy ? null : _resend,
                child: Text(messages.t('auth.resend')),
              ),
            ],
            if (_info != null) ...[const SizedBox(height: 16), Text(_info!, key: const Key('register-info'))],
            if (_error != null) ...[const SizedBox(height: 16), Text(_error!, key: const Key('register-error'))],
            if (!_verify) ...[
              const SizedBox(height: 24),
              FilledButton(
                key: const Key('register-submit'),
                onPressed: _busy ? null : _submit,
                child: Text(messages.t('auth.registerSubmit')),
              ),
            ],
            TextButton(
              key: const Key('leave-register'),
              onPressed: widget.onLeave,
              child: Text(messages.t('auth.goToLogin')),
            ),
          ],
        ),
      ),
    );
  }
}
