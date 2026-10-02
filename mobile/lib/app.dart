import 'package:flutter/material.dart';

import 'api/auth_api.dart';
import 'auth/session_controller.dart';
import 'auth/token_store.dart';
import 'i18n/locale_controller.dart';
import 'i18n/messages.dart';
import 'screens/activate_screen.dart';
import 'screens/forgot_password_screen.dart';
import 'screens/home_screen.dart';
import 'screens/login_screen.dart';
import 'screens/register_screen.dart';
import 'theme.dart';

enum _Guest { login, register, activate, forgot }

class CoachingApp extends StatefulWidget {
  const CoachingApp({required this.api, required this.tokens, super.key});

  final AuthApi api;
  final TokenStore tokens;

  @override
  State<CoachingApp> createState() => _CoachingAppState();
}

class _CoachingAppState extends State<CoachingApp> {
  final LocaleController _locale = LocaleController();
  late final SessionController _session;
  _Guest _guest = _Guest.login;
  String? _loginNotice;
  bool _wasSignedIn = false;

  @override
  void initState() {
    super.initState();
    _session = SessionController(api: widget.api, tokens: widget.tokens);
    widget.api.bind(tokens: widget.tokens, onSessionCleared: _session.markSignedOut);
    _session.addListener(_onSession);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _session.restore();
    });
  }

  void _onSession() {
    if (_session.phase == SessionPhase.signedOut && _wasSignedIn) {
      _guest = _Guest.login;
    }
    _wasSignedIn = _session.phase == SessionPhase.signedIn;
    if (mounted) {
      setState(() {});
    }
  }

  @override
  void dispose() {
    _session.removeListener(_onSession);
    _session.dispose();
    _locale.dispose();
    super.dispose();
  }

  void _showLogin({String? notice}) {
    setState(() {
      _guest = _Guest.login;
      _loginNotice = notice;
    });
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: _locale,
      builder: (context, _) {
        final messages = _locale.messages;
        return MaterialApp(
          title: messages.t('app.title'),
          theme: appTheme,
          home: _body(messages),
        );
      },
    );
  }

  Widget _body(Messages messages) {
    if (_session.phase == SessionPhase.restoring) {
      return Scaffold(body: Center(child: Text(messages.t('auth.restoring'))));
    }
    if (_session.phase == SessionPhase.signedIn) {
      return HomeScreen(locale: _locale, api: widget.api, onLogout: _session.logout);
    }
    switch (_guest) {
      case _Guest.login:
        return LoginScreen(
          locale: _locale,
          api: widget.api,
          tokens: widget.tokens,
          notice: _loginNotice,
          onSignedIn: () {
            _loginNotice = null;
            _session.markSignedIn();
          },
          onRegister: () => setState(() {
            _loginNotice = null;
            _guest = _Guest.register;
          }),
          onActivate: () => setState(() {
            _loginNotice = null;
            _guest = _Guest.activate;
          }),
          onForgot: () => setState(() {
            _loginNotice = null;
            _guest = _Guest.forgot;
          }),
        );
      case _Guest.register:
        return RegisterScreen(locale: _locale, api: widget.api, onLeave: () => _showLogin());
      case _Guest.activate:
        return ActivateScreen(
          locale: _locale,
          api: widget.api,
          tokens: widget.tokens,
          onSignedIn: _session.markSignedIn,
          onLeave: () => _showLogin(),
        );
      case _Guest.forgot:
        return ForgotPasswordScreen(
          locale: _locale,
          api: widget.api,
          tokens: widget.tokens,
          onLeave: () => _showLogin(),
          onReset: () => _showLogin(notice: _locale.messages.t('auth.resetDone')),
        );
    }
  }
}
