import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:mobile/api/auth_api.dart';
import 'package:mobile/app.dart';
import 'package:mobile/i18n/en.dart';

import 'package:mobile/files/cover_byte_cache.dart';

import 'support/catalog_http.dart';
import 'support/course_fixtures.dart';
import 'support/jwt.dart';
import 'support/memory_token_store.dart';

void main() {
  const baseUrl = 'http://10.0.2.2:8080';

  setUp(() => CoverByteCache.instance.clearForTesting());
  const secretVideoRef = 'dQw4w9WgXcQ';

  http.Response me() {
    return http.Response(
      jsonEncode({
        'id': 'user-1',
        'fullName': 'Student',
        'role': 'STUDENT',
        'status': 'ACTIVE',
      }),
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

  Future<List<http.Request>> openCourseList(
    WidgetTester tester, {
    required http.Response Function(http.Request request) handler,
  }) async {
    final store = MemoryTokenStore();
    final access = unsignedJwt(role: 'STUDENT');
    await store.writeAccessToken(access);
    await store.writeRefreshToken('refresh-1');
    final requests = <http.Request>[];
    final api = AuthApi(
      baseUrl: baseUrl,
      client: MockClient((request) async {
        requests.add(request);
        return handler(request);
      }),
    );
    await tester.pumpWidget(CoachingApp(key: UniqueKey(), api: api, tokens: store));
    await tester.pumpAndSettle();
    return requests;
  }

  testWidgets('detail renders tree titles without video_ref from the catalog payload', (tester) async {
    await openCourseList(tester, handler: (request) {
      final catalog = tryCatalogList(request);
      if (catalog != null) {
        if (request.url.path.endsWith('/catalog/courses')) {
          return catalogPage([courseSummaryJson(id: courseId, titleEn: 'Freemium Course')]);
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
        return courseDetailResponse(id: courseId, accessType: 'FREEMIUM', tree: freemiumTree());
      }
      return catalogError();
    });

    await tester.tap(find.text('Freemium Course'));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('course-detail')), findsOneWidget);
    expect(find.byKey(const Key('course-tree')), findsOneWidget);
    expect(find.text('Free lecture'), findsOneWidget);
    expect(find.text('Paid lecture'), findsOneWidget);
    expect(find.textContaining(secretVideoRef), findsNothing);
    expect(find.byKey(const Key('course-purchase')), findsOneWidget);
  });

  testWidgets('opening a free lecture requests the lecture route and shows the player', (tester) async {
    final requests = await openCourseList(tester, handler: (request) {
      final catalog = tryCatalogList(request);
      if (catalog != null) {
        if (request.url.path.endsWith('/catalog/courses')) {
          return catalogPage([courseSummaryJson(id: courseId, titleEn: 'Freemium Course')]);
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
        return courseDetailResponse(id: courseId, accessType: 'FREEMIUM', tree: freemiumTree());
      }
      if (request.url.path.endsWith('/lectures/$freeLectureId')) {
        return lectureContentResponse(id: freeLectureId, videoRef: secretVideoRef);
      }
      return catalogError();
    });

    await tester.tap(find.text('Freemium Course'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Free lecture'));
    await tester.pumpAndSettle();

    expect(requests.any((r) => r.url.path.endsWith('/lectures/$freeLectureId')), isTrue);
    expect(find.byKey(const Key('lecture-player')), findsOneWidget);
    expect(find.byKey(const Key('lecture-notes')), findsOneWidget);
    expect(find.textContaining(secretVideoRef), findsNothing);
  });

  testWidgets('a paid lecture shows locked state without the video id', (tester) async {
    tester.view.physicalSize = const Size(800, 1200);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);
    await openCourseList(tester, handler: (request) {
      final catalog = tryCatalogList(request);
      if (catalog != null) {
        if (request.url.path.endsWith('/catalog/courses')) {
          return catalogPage([courseSummaryJson(id: courseId, titleEn: 'Freemium Course')]);
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
        return courseDetailResponse(id: courseId, accessType: 'FREEMIUM', tree: freemiumTree());
      }
      if (request.url.path.endsWith('/lectures/$paidLectureId')) {
        return enrollmentRequired();
      }
      return catalogError();
    });

    await tester.tap(find.text('Freemium Course'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.byKey(const Key('course-tree-lecture-$paidLectureId')));
    await tester.tap(find.byKey(const Key('course-tree-lecture-$paidLectureId')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('lecture-locked')), findsOneWidget);
    expect(find.text(english.t('catalog.lectureLocked')), findsOneWidget);
    expect(find.byKey(const Key('lecture-player')), findsNothing);
    expect(find.textContaining(secretVideoRef), findsNothing);
  });

  testWidgets('a full-free course has no purchase control and opens a lecture', (tester) async {
    await openCourseList(tester, handler: (request) {
      final catalog = tryCatalogList(request);
      if (catalog != null) {
        if (request.url.path.endsWith('/catalog/courses')) {
          return catalogPage([
            courseSummaryJson(id: fullFreeCourseId, titleEn: 'Full Free Course', accessType: 'FULL_FREE'),
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
      if (request.url.path.endsWith('/catalog/courses/$fullFreeCourseId')) {
        return courseDetailResponse(
          id: fullFreeCourseId,
          accessType: 'FULL_FREE',
          tree: [
            {
              'id': paidLectureId,
              'parentId': null,
              'nodeType': 'LECTURE',
              'title': 'Any lecture',
              'position': 0,
              'free': false,
            },
          ],
        );
      }
      if (request.url.path.endsWith('/lectures/$paidLectureId')) {
        return lectureContentResponse(id: paidLectureId, videoRef: secretVideoRef);
      }
      return catalogError();
    });

    await tester.tap(find.text('Full Free Course'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('course-purchase')), findsNothing);

    await tester.tap(find.text('Any lecture'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('lecture-player')), findsOneWidget);
  });

  testWidgets('opening a material requests GET /files/{id} through the API client', (tester) async {
    tester.view.physicalSize = const Size(800, 1200);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);
    final requests = await openCourseList(tester, handler: (request) {
      final catalog = tryCatalogList(request);
      if (catalog != null) {
        if (request.url.path.endsWith('/catalog/courses')) {
          return catalogPage([courseSummaryJson(id: courseId, titleEn: 'Freemium Course')]);
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
        return courseDetailResponse(id: courseId, accessType: 'FREEMIUM', tree: freemiumTree());
      }
      if (request.url.path.endsWith('/lectures/$freeLectureId')) {
        return lectureContentResponse(id: freeLectureId, videoRef: secretVideoRef);
      }
      if (request.url.path.endsWith('/files/$materialId')) {
        return fileBytesResponse();
      }
      return catalogError();
    });

    await tester.tap(find.text('Freemium Course'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Free lecture'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('notes.pdf'));
    await tester.pumpAndSettle();

    final fileRequest = requests.firstWhere((r) => r.url.path.contains('/files/$materialId'));
    expect(fileRequest.method, 'GET');
    expect(fileRequest.url.toString(), isNot(contains('public')));
    expect(find.text('notes.pdf'), findsOneWidget);
  });
}
