import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:mobile/api/auth_api.dart';
import 'package:mobile/app.dart';
import 'package:mobile/files/cover_byte_cache.dart';
import 'package:mobile/files/file_opener.dart';
import 'package:mobile/i18n/en.dart';

import 'support/book_fixtures.dart';
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
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.pumpAndSettle();
    return requests;
  }

  testWidgets('course list and detail request cover files and skip missing covers', (tester) async {
    final requests = await openCourseList(tester, handler: (request) {
      final catalog = tryCatalogList(request);
      if (catalog != null) {
        if (request.url.path.endsWith('/catalog/courses')) {
          return catalogPage([
            courseSummaryJson(id: courseId, titleEn: 'Covered Course', coverFileId: courseCoverFileId),
            courseSummaryJson(id: 'course-no-cover', titleEn: 'Plain Course'),
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
        return tinyPngBytes();
      }
      if (request.url.path.endsWith('/catalog/courses/$courseId')) {
        return courseDetailResponse(
          id: courseId,
          accessType: 'FREEMIUM',
          tree: freemiumTree(),
          coverFileId: courseCoverFileId,
        );
      }
      if (request.url.path.endsWith('/catalog/courses/course-no-cover')) {
        return courseDetailResponse(id: 'course-no-cover', accessType: 'FREEMIUM', tree: freemiumTree());
      }
      return catalogError();
    });

    expect(requests.any((r) => r.url.path.endsWith('/files/$courseCoverFileId')), isTrue);
    expect(find.byKey(const Key('course-list-cover-$courseId')), findsOneWidget);
    expect(find.byKey(const Key('course-list-cover-course-no-cover')), findsNothing);

    await tester.tap(find.text('Covered Course'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('course-cover')), findsOneWidget);
    final coverRequests = requests.where((r) => r.url.path.endsWith('/files/$courseCoverFileId')).length;
    expect(coverRequests, 1);

    await tester.pageBack();
    await tester.pumpAndSettle();
    await tester.tap(find.text('Covered Course'));
    await tester.pumpAndSettle();
    final coverRequestsAfterReturn = requests.where((r) => r.url.path.endsWith('/files/$courseCoverFileId')).length;
    expect(coverRequestsAfterReturn, 1);
  });

  testWidgets('outline nests sections and keeps course-level lectures separate', (tester) async {
    await openCourseList(tester, handler: (request) {
      final catalog = tryCatalogList(request);
      if (catalog != null) {
        if (request.url.path.endsWith('/catalog/courses')) {
          return catalogPage([courseSummaryJson(id: courseId, titleEn: 'Outline Course')]);
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
        return courseDetailResponse(id: courseId, accessType: 'FREEMIUM', tree: nestedOutlineTree());
      }
      return catalogError();
    });

    await tester.tap(find.text('Outline Course'));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('course-tree-section-section-1')), findsOneWidget);
    expect(find.byKey(const Key('course-tree-nested-section-section-nested')), findsOneWidget);
    expect(find.byType(Card), findsOneWidget);
    expect(find.byKey(const Key('course-tree-course-lectures')), findsNothing);
    expect(find.byKey(const Key('course-tree-lecture-lecture-course')), findsOneWidget);
    expect(find.text('Course lecture'), findsOneWidget);
    expect(find.text('Free lecture'), findsOneWidget);
  });

  testWidgets('category path line switches language without showing both titles', (tester) async {
    await openCourseList(tester, handler: (request) {
      final catalog = tryCatalogList(request);
      if (catalog != null) {
        if (request.url.path.endsWith('/catalog/courses')) {
          return catalogPage([courseSummaryJson(id: courseId, titleEn: 'Placed Course')]);
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
          tree: freemiumTree(),
          categoryPath: [
            {'titleEn': 'Science', 'titleBn': 'বিজ্ঞান'},
            {'titleEn': 'Physics', 'titleBn': 'পদার্থবিজ্ঞান'},
          ],
        );
      }
      return catalogError();
    });

    await tester.tap(find.text('Placed Course'));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('course-placement')), findsOneWidget);
    expect(find.byKey(const Key('course-placement-path')), findsOneWidget);
    expect(find.text('Science › Physics'), findsOneWidget);
    expect(find.textContaining('বিজ্ঞান'), findsNothing);

    await tester.tap(find.text(english.t('locale.bn')));
    await tester.pumpAndSettle();

    expect(find.text('বিজ্ঞান › পদার্থবিজ্ঞান'), findsOneWidget);
    expect(find.textContaining('Science'), findsNothing);
  });

  testWidgets('course files open images and pdfs without public URLs', (tester) async {
    tester.view.physicalSize = const Size(800, 1200);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);
    var pdfHandoffCalled = false;
    final previousPdfHandler = FileOpener.instance.handoffPdf;
    FileOpener.instance.handoffPdf = (file, {required String fileId}) async {
      pdfHandoffCalled = true;
      expect(fileId, materialId);
      return true;
    };
    addTearDown(() => FileOpener.instance.handoffPdf = previousPdfHandler);
    final requests = await openCourseList(tester, handler: (request) {
      final catalog = tryCatalogList(request);
      if (catalog != null) {
        if (request.url.path.endsWith('/catalog/courses')) {
          return catalogPage([courseSummaryJson(id: courseId, titleEn: 'Files Course')]);
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
      if (request.url.path.endsWith('/me/courses')) {
        return myCoursesList();
      }
      if (request.url.path.endsWith('/catalog/courses/$courseId')) {
        return courseDetailResponse(
          id: courseId,
          accessType: 'FREEMIUM',
          tree: const [],
          coverFileId: courseCoverFileId,
          courseMaterials: [
            {
              'id': courseFileId,
              'originalFileName': 'diagram.png',
              'contentType': 'image/png',
              'byteSize': 68,
              'free': true,
            },
            {
              'id': materialId,
              'originalFileName': 'handout.pdf',
              'contentType': 'application/pdf',
              'byteSize': 4,
              'free': true,
            },
            {
              'id': 'locked-file',
              'originalFileName': 'secret.pdf',
              'contentType': 'application/pdf',
              'byteSize': 4,
              'free': false,
            },
          ],
        );
      }
      if (request.url.path.endsWith('/files/$courseFileId')) {
        return tinyPngBytes();
      }
      if (request.url.path.endsWith('/files/$materialId')) {
        return fileBytesResponse();
      }
      if (request.url.path.endsWith('/files/locked-file')) {
        return enrollmentRequired();
      }
      return catalogError();
    });

    await tester.tap(find.text('Files Course'));
    await tester.pumpAndSettle();

    expect(find.text('diagram.png'), findsOneWidget);
    expect(find.text('handout.pdf'), findsOneWidget);
    expect(find.text('secret.pdf'), findsOneWidget);
    expect(find.text(courseCoverFileId), findsNothing);

    await tester.tap(find.text('diagram.png'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('file-image-viewer')), findsOneWidget);
    final imageRequest = requests.firstWhere((r) => r.url.path.endsWith('/files/$courseFileId'));
    expect(imageRequest.url.toString(), isNot(contains('public')));

    await tester.pageBack();
    await tester.pumpAndSettle();

    await tester.tap(find.text('handout.pdf'));
    await tester.pumpAndSettle();
    expect(pdfHandoffCalled, isTrue);
    expect(find.byKey(const Key('pdf-opening')), findsNothing);
    expect(find.byKey(const Key('file-image-viewer')), findsNothing);

    await tester.tap(find.text('secret.pdf'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('file-image-viewer')), findsNothing);
    expect(find.byKey(const Key('pdf-opening')), findsNothing);
    expect(find.text(english.t('catalog.lectureLocked')), findsOneWidget);
  });

  testWidgets('locked lecture does not show lecture file names', (tester) async {
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
    expect(find.text('notes.pdf'), findsNothing);
  });

  testWidgets('course list does not request admin category routes', (tester) async {
    final requests = await openCourseList(tester, handler: (request) {
      final catalog = tryCatalogList(request);
      if (catalog != null) {
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
      if (request.url.path.endsWith('/catalog/courses')) {
        return catalogPage([courseSummaryJson(id: courseId, titleEn: 'Physics')]);
      }
      if (request.url.path.endsWith('/catalog/books')) {
        return catalogPage([]);
      }
      return catalogError();
    });

    expect(
      requests.any((r) => r.url.path.endsWith('/course-categories') && !r.url.path.contains('/catalog/')),
      isFalse,
    );
    expect(requests.any((r) => r.url.path.endsWith('/catalog/course-categories')), isTrue);
  });

  testWidgets('section children render in position order across lectures and nested sections', (tester) async {
    await openCourseList(tester, handler: (request) {
      final catalog = tryCatalogList(request);
      if (catalog != null) {
        if (request.url.path.endsWith('/catalog/courses')) {
          return catalogPage([courseSummaryJson(id: courseId, titleEn: 'Mixed Course')]);
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
        return courseDetailResponse(id: courseId, accessType: 'FREEMIUM', tree: mixedSectionSiblingTree());
      }
      return catalogError();
    });

    await tester.tap(find.text('Mixed Course'));
    await tester.pumpAndSettle();

    final nestedY = tester.getTopLeft(find.text('Nested section')).dy;
    final lectureY = tester.getTopLeft(find.text('Paid under section')).dy;
    expect(nestedY, lessThan(lectureY));
  });

  testWidgets('enrolled student sees paid lectures as openable on the outline', (tester) async {
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
        return myCoursesList(courseIds: [courseId]);
      }
      if (request.url.path.endsWith('/catalog/courses/$courseId')) {
        return courseDetailResponse(id: courseId, accessType: 'FREEMIUM', tree: freemiumTree());
      }
      return catalogError();
    });

    await tester.tap(find.text('Freemium Course'));
    await tester.pumpAndSettle();

    final paidTile = tester.widget<ListTile>(find.byKey(const Key('course-tree-lecture-$paidLectureId')));
    expect((paidTile.leading! as Icon).icon, Icons.play_circle_outline);
  });

  testWidgets('course detail loads GET /me/courses with the catalog course', (tester) async {
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
      return catalogError();
    });

    await tester.tap(find.text('Freemium Course'));
    await tester.pumpAndSettle();

    expect(requests.any((r) => r.url.path.endsWith('/me/courses')), isTrue);
  });
}
