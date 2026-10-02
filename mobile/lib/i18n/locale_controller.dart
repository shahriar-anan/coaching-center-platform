import 'package:flutter/foundation.dart';

import 'bn.dart';
import 'en.dart';
import 'messages.dart';

class LocaleController extends ChangeNotifier {
  String _code = 'en';

  String get code => _code;

  Messages get messages => _code == 'bn' ? bangla : english;

  void toggle() {
    _code = _code == 'en' ? 'bn' : 'en';
    notifyListeners();
  }
}
