import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/config/api_base_url.dart';

void main() {
  test('dev flavor default base URL is the emulator host and prod has none', () {
    expect(resolveApiBaseUrl(flavor: 'dev', definedUrl: ''), defaultApiBaseUrl);
    expect(resolveApiBaseUrl(flavor: 'prod', definedUrl: ''), '');
    expect(resolveApiBaseUrl(flavor: 'prod', definedUrl: 'https://api.example.com/'), 'https://api.example.com');
    expect(resolveApiBaseUrl(flavor: 'dev', definedUrl: 'http://192.168.0.8:8080'), 'http://192.168.0.8:8080');
    expect(apiBaseUrl(), defaultApiBaseUrl);
  });

  test('dev allows cleartext and prod does not', () {
    final dev = File('android/app/src/dev/AndroidManifest.xml').readAsStringSync();
    final prod = File('android/app/src/prod/AndroidManifest.xml').readAsStringSync();
    final debug = File('android/app/src/debug/AndroidManifest.xml').readAsStringSync();
    final gradle = File('android/app/build.gradle.kts').readAsStringSync();

    expect(dev, contains('android:usesCleartextTraffic="true"'));
    expect(prod, contains('android:usesCleartextTraffic="false"'));
    expect(debug.contains('usesCleartextTraffic'), isFalse);
    expect(gradle, contains('create("dev")'));
    expect(gradle, contains('create("prod")'));
  });
}
