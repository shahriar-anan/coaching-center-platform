import 'package:flutter/widgets.dart';

import 'api/auth_api.dart';
import 'app.dart';
import 'auth/secure_token_store.dart';
import 'config/api_base_url.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(CoachingApp(api: AuthApi(baseUrl: apiBaseUrl()), tokens: SecureTokenStore()));
}
