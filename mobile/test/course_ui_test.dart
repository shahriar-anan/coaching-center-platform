import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:mobile/api/auth_api.dart';
import 'package:mobile/app.dart';
import 'package:mobile/files/cover_byte_cache.dart';
import 'package:mobile/files/file_opener.dart';
import 'package:mobile/i18n/en.dart';

import 'support/catalog_http.dart';
import 'support/course_fixtures.dart';
import 'support/jwt.dart';
import 'support/memory_token_store.dart';

void main() {
  const baseUrl = 'http://10.0.2.2:8080';

  setUp(() => CoverByteCache.instance.clearForTesting());

  http.Response me() {
    return http.Response(
      jsonEncode({'id': 'user-1', 'fullName': 'Student', 'role': 'STUDENT', 'status': 'ACTIVE'}),
      200,
      headers: {'content-type': 'application/json'},
    );
  }

  http.Response tokens(String accessToken, String refreshToken) {
    return http.Response(
      jsonEncode({'accessToken': accessToken, 'expiresInSeconds': 900, 'refreshToken': refreshToken}),
      200,
      headers: {'content-type': 'application/json'},
    );
  }

  http.Response oversizedPng() {
    final bytes = Uint8List(CoverByteCache.maxDisplayBytes + 1);
    return http.Response.bytes(
      bytes,
      200,
      headers: {
        'content-type': 'image/png',
        'content-disposition': 'inline; filename="huge.png"',
      },
    );
  }

  Future<void> pumpCourseDetail(
    WidgetTester tester, {
    required Future<http.Response> Function(http.Request request) handler,
  }) async {
    final store = MemoryTokenStore();
    final access = unsignedJwt(role: 'STUDENT');
    await store.writeAccessToken(access);
    await store.writeRefreshToken('refresh-1');
    final api = AuthApi(
      baseUrl: baseUrl,
      client: MockClient(handler),
    );
    await tester.pumpWidget(CoachingApp(key: UniqueKey(), api: api, tokens: store));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.pumpAndSettle();
    await tester.tap(find.text('UI Course'));
    await tester.pumpAndSettle();
  }

  testWidgets('an oversized cover shows the too-large string and not an image', (tester) async {
    await pumpCourseDetail(tester, handler: (request) async {
      final catalog = tryCatalogList(request);
      if (catalog != null) {
        if (request.url.path.endsWith('/catalog/courses')) {
          return catalogPage([
            courseSummaryJson(id: courseId, titleEn: 'UI Course', coverFileId: courseCoverFileId),
          ]);
        }
        return catalog;
      }
      if (request.url.path.endsWith('/auth/refresh')) {
        return tokens(unsignedJwt(role: 'STUDENT'), 'refresh-1');
      }
      if (request.url.path.endsWith('/me')) {
        return me();
      }
      if (request.url.path.endsWith('/me/courses')) {
        return myCoursesList();
      }
      if (request.url.path.endsWith('/files/$courseCoverFileId')) {
        return oversizedPng();
      }
      if (request.url.path.endsWith('/catalog/courses/$courseId')) {
        return courseDetailResponse(
          id: courseId,
          accessType: 'FREEMIUM',
          tree: const [],
          coverFileId: courseCoverFileId,
        );
      }
      return catalogError();
    });

    expect(find.byKey(const Key('course-cover-too-large')), findsOneWidget);
    expect(find.byKey(const Key('course-cover')), findsNothing);
    expect(find.text(english.t('catalog.coverTooLarge')), findsOneWidget);
  });

  testWidgets('placement path starts in Bangla when the app locale is Bangla', (tester) async {
    final store = MemoryTokenStore();
    final access = unsignedJwt(role: 'STUDENT');
    await store.writeAccessToken(access);
    await store.writeRefreshToken('refresh-1');
    final api = AuthApi(
      baseUrl: baseUrl,
      client: MockClient((request) async {
        final catalog = tryCatalogList(request);
        if (catalog != null) {
          if (request.url.path.endsWith('/catalog/courses')) {
            return catalogPage([courseSummaryJson(id: courseId, titleEn: 'UI Course')]);
          }
          return catalog;
        }
        if (request.url.path.endsWith('/auth/refresh')) {
          return tokens(unsignedJwt(role: 'STUDENT'), 'refresh-1');
        }
        if (request.url.path.endsWith('/me')) {
          return me();
        }
        if (request.url.path.endsWith('/me/courses')) {
          return myCoursesList();
        }
        if (request.url.path.endsWith('/catalog/courses/$courseId')) {
          return courseDetailResponse(
            id: courseId,
            accessType: 'FREEMIUM',
            tree: const [],
            categoryPath: [
              {'titleEn': 'Science', 'titleBn': 'বিজ্ঞান'},
            ],
          );
        }
        return catalogError();
      }),
    );
    await tester.pumpWidget(CoachingApp(key: UniqueKey(), api: api, tokens: store));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('locale-toggle')));
    await tester.pump();
    await tester.tap(find.text('শিরোনাম'));
    await tester.pumpAndSettle();

    expect(find.text('বিজ্ঞান'), findsOneWidget);
    expect(find.text('Science'), findsNothing);
  });

  testWidgets('a course file row shows a progress indicator while downloading', (tester) async {
    tester.view.physicalSize = const Size(800, 1200);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);
    final previousPdfHandler = FileOpener.instance.handoffPdf;
    FileOpener.instance.handoffPdf = (_, {required String fileId}) async => true;
    addTearDown(() => FileOpener.instance.handoffPdf = previousPdfHandler);

    final store = MemoryTokenStore();
    final access = unsignedJwt(role: 'STUDENT');
    await store.writeAccessToken(access);
    await store.writeRefreshToken('refresh-1');
    final api = AuthApi(
      baseUrl: baseUrl,
      client: MockClient((request) async {
        final catalog = tryCatalogList(request);
        if (catalog != null) {
          if (request.url.path.endsWith('/catalog/courses')) {
            return catalogPage([courseSummaryJson(id: courseId, titleEn: 'UI Course')]);
          }
          return catalog;
        }
        if (request.url.path.endsWith('/auth/refresh')) {
          return tokens(unsignedJwt(role: 'STUDENT'), 'refresh-1');
        }
        if (request.url.path.endsWith('/me')) {
          return me();
        }
        if (request.url.path.endsWith('/me/courses')) {
          return myCoursesList(courseIds: [courseId]);
        }
        if (request.url.path.endsWith('/catalog/courses/$courseId')) {
          return courseDetailResponse(
            id: courseId,
            accessType: 'FREEMIUM',
            tree: const [],
            courseMaterials: [
              {
                'id': courseFileId,
                'originalFileName': 'notes.pdf',
                'contentType': 'application/pdf',
                'byteSize': 4,
                'free': true,
              },
            ],
          );
        }
        if (request.url.path.endsWith('/files/$courseFileId')) {
          await Future<void>.delayed(const Duration(milliseconds: 200));
          return fileBytesResponse();
        }
        return catalogError();
      }),
    );
    await tester.pumpWidget(CoachingApp(key: UniqueKey(), api: api, tokens: store));
    await tester.pumpAndSettle();
    await tester.tap(find.text('UI Course'));
    await tester.pumpAndSettle();

    await tester.tap(find.text('notes.pdf'));
    await tester.pump();
    expect(find.byKey(Key('course-file-loading-$courseFileId')), findsOneWidget);
    await tester.pumpAndSettle(const Duration(milliseconds: 300));
    expect(find.byKey(Key('course-file-loading-$courseFileId')), findsNothing);
  });
}
