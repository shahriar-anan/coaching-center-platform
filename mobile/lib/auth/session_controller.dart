import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import '../api/api_exception.dart';
import '../api/auth_api.dart';
import 'jwt_role.dart';
import 'token_store.dart';

enum SessionPhase { restoring, signedOut, signedIn }

/// Launch restore, logout, and the signed-in flag. Tokens stay in [TokenStore].
class SessionController extends ChangeNotifier {
  SessionController({required this.api, required this.tokens});

  final AuthApi api;
  final TokenStore tokens;

  SessionPhase phase = SessionPhase.restoring;

  /// When a refresh token is stored, exchange it and open the home.
  /// Failure clears both tokens and leaves the student signed out.
  Future<void> restore() async {
    final refresh = await tokens.readRefreshToken();
    if (refresh == null || refresh.isEmpty) {
      phase = SessionPhase.signedOut;
      notifyListeners();
      return;
    }
    try {
      final pair = await api.refresh(refreshToken: refresh);
      if (roleFromAccessToken(pair.accessToken) != 'STUDENT') {
        await tokens.clear();
        phase = SessionPhase.signedOut;
        notifyListeners();
        return;
      }
      await tokens.writeAccessToken(pair.accessToken);
      await tokens.writeRefreshToken(pair.refreshToken);
      phase = SessionPhase.signedIn;
      notifyListeners();
    } on ApiException {
      await _clearToLogin();
    } on http.ClientException {
      await _clearToLogin();
    }
  }

  void markSignedIn() {
    phase = SessionPhase.signedIn;
    notifyListeners();
  }

  void markSignedOut() {
    phase = SessionPhase.signedOut;
    notifyListeners();
  }

  /// Sends the stored refresh token with the bearer access token, then clears storage.
  Future<void> logout() async {
    final access = await tokens.readAccessToken();
    final refresh = await tokens.readRefreshToken();
    try {
      if (access != null && access.isNotEmpty && refresh != null && refresh.isNotEmpty) {
        await api.logout(accessToken: access, refreshToken: refresh);
      }
    } finally {
      await tokens.clear();
      phase = SessionPhase.signedOut;
      notifyListeners();
    }
  }

  Future<void> _clearToLogin() async {
    await tokens.clear();
    phase = SessionPhase.signedOut;
    notifyListeners();
  }
}
