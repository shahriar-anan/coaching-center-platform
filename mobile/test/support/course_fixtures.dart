import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:mobile/api/catalog_models.dart';

const courseId = 'course-1';
const freeLectureId = 'lecture-free';
const paidLectureId = 'lecture-paid';
const fullFreeCourseId = 'course-full-free';
const materialId = 'material-1';
const courseCoverFileId = 'course-cover-1';
const courseFileId = 'course-file-1';

Map<String, dynamic> courseSummaryJson({
  required String id,
  required String titleEn,
  String accessType = 'FREEMIUM',
  String? coverFileId,
}) {
  return {
    'id': id,
    'titleEn': titleEn,
    'titleBn': 'শিরোনাম',
    'price': accessType == 'FULL_FREE' ? null : 100,
    'deliveryMode': 'ONLINE',
    'accessType': accessType,
    'coverFileId': coverFileId,
  };
}

http.Response courseDetailResponse({
  required String id,
  required String accessType,
  required List<Map<String, dynamic>> tree,
  String deliveryMode = 'ONLINE',
  String? coverFileId,
  List<Map<String, dynamic>>? categoryPath,
  List<Map<String, dynamic>>? courseMaterials,
}) {
  return http.Response(
    jsonEncode({
      'id': id,
      'titleEn': accessType == 'FULL_FREE' ? 'Full Free Course' : 'Freemium Course',
      'titleBn': 'কোর্স',
      'description': 'Course description',
      'contentLanguage': 'EN',
      'price': accessType == 'FULL_FREE' ? null : 100,
      'deliveryMode': deliveryMode,
      'accessType': accessType,
      'coverFileId': coverFileId,
      'contentTree': tree,
      if (categoryPath != null) 'categoryPath': categoryPath,
      if (courseMaterials != null) 'courseMaterials': courseMaterials,
    }),
    200,
    headers: {'content-type': 'application/json'},
  );
}

http.Response myCoursesList({List<String> courseIds = const []}) {
  return http.Response(
    jsonEncode(
      courseIds
          .map(
            (id) => {
              'courseId': id,
              'titleEn': 'Course',
              'titleBn': 'কোর্স',
            },
          )
          .toList(),
    ),
    200,
    headers: {'content-type': 'application/json'},
  );
}

List<Map<String, dynamic>> mixedSectionSiblingTree() {
  return [
    {'id': 'section-1', 'parentId': null, 'nodeType': 'SECTION', 'title': 'Section one', 'position': 0, 'free': false},
    {
      'id': 'section-nested',
      'parentId': 'section-1',
      'nodeType': 'SECTION',
      'title': 'Nested section',
      'position': 0,
      'free': false,
    },
    {
      'id': paidLectureId,
      'parentId': 'section-1',
      'nodeType': 'LECTURE',
      'title': 'Paid under section',
      'position': 1,
      'free': false,
    },
  ];
}

List<Map<String, dynamic>> nestedOutlineTree() {
  return [
    {'id': 'section-1', 'parentId': null, 'nodeType': 'SECTION', 'title': 'Section one', 'position': 0, 'free': false},
    {
      'id': 'section-nested',
      'parentId': 'section-1',
      'nodeType': 'SECTION',
      'title': 'Nested section',
      'position': 0,
      'free': false,
    },
    {
      'id': freeLectureId,
      'parentId': 'section-nested',
      'nodeType': 'LECTURE',
      'title': 'Free lecture',
      'position': 0,
      'free': true,
    },
    {
      'id': 'lecture-course',
      'parentId': null,
      'nodeType': 'LECTURE',
      'title': 'Course lecture',
      'position': 1,
      'free': true,
    },
  ];
}

List<Map<String, dynamic>> freemiumTree() {
  return [
    {'id': 'section-1', 'parentId': null, 'nodeType': 'SECTION', 'title': 'Section one', 'position': 0, 'free': false},
    {
      'id': freeLectureId,
      'parentId': 'section-1',
      'nodeType': 'LECTURE',
      'title': 'Free lecture',
      'position': 1,
      'free': true,
    },
    {
      'id': paidLectureId,
      'parentId': 'section-1',
      'nodeType': 'LECTURE',
      'title': 'Paid lecture',
      'position': 2,
      'free': false,
    },
  ];
}

http.Response lectureContentResponse({
  required String id,
  required String videoRef,
  String notes = 'Lecture notes',
  List<Map<String, dynamic>>? materials,
}) {
  final materialEntries = materials ??
      [
        {
          'id': materialId,
          'originalFileName': 'notes.pdf',
          'contentType': 'application/pdf',
          'byteSize': 4,
        },
      ];
  return http.Response(
    jsonEncode({
      'id': id,
      'title': 'Lecture',
      'videoRef': videoRef,
      'notes': notes,
      'materialIds': materialEntries.map((entry) => entry['id']).toList(),
      'materials': materialEntries,
    }),
    200,
    headers: {'content-type': 'application/json'},
  );
}

http.Response materialForbidden() {
  return http.Response(
    jsonEncode({
      'code': 'MATERIAL_FORBIDDEN',
      'message': 'Forbidden.',
      'timestamp': '2026-10-02T00:00:00Z',
      'requestId': 'req-1',
    }),
    403,
    headers: {'content-type': 'application/json'},
  );
}

http.Response enrollmentRequired() {
  return http.Response(
    jsonEncode({
      'code': 'ENROLLMENT_REQUIRED',
      'message': 'Enrollment is required.',
      'timestamp': '2026-10-02T00:00:00Z',
      'requestId': 'req-1',
    }),
    403,
    headers: {'content-type': 'application/json'},
  );
}

CourseDetail courseForPurchase({String deliveryMode = 'ONLINE'}) {
  return CourseDetail(
    id: courseId,
    titleEn: 'Freemium Course',
    titleBn: 'কোর্স',
    description: 'Course description',
    contentLanguage: 'EN',
    price: 100,
    deliveryMode: deliveryMode,
    accessType: 'FREEMIUM',
    contentTree: const [],
  );
}

http.Response purchaseCreated({
  String paymentNumber = '01700000000',
  String transactionRef = 'trx-1',
}) {
  return http.Response(
    jsonEncode({
      'enrollmentId': 'enrollment-1',
      'paymentId': 'payment-1',
      'enrollmentStatus': 'AWAITING_PAYMENT',
      'paymentStatus': 'PENDING',
      'amount': 100,
      'method': 'BKASH',
      'transactionRef': transactionRef,
      'paymentNumber': paymentNumber,
    }),
    201,
    headers: {'content-type': 'application/json'},
  );
}

http.Response purchaseHistoryList({required String courseId, required String transactionRef}) {
  return http.Response(
    jsonEncode([
      {
        'enrollmentId': 'enrollment-1',
        'courseId': courseId,
        'enrollmentStatus': 'AWAITING_PAYMENT',
        'paymentStatus': 'PENDING',
        'amount': 100,
        'method': 'BKASH',
        'transactionRef': transactionRef,
      },
    ]),
    200,
    headers: {'content-type': 'application/json'},
  );
}

http.Response apiConflict(String code, {String message = 'Conflict'}) {
  return http.Response(
    jsonEncode({'code': code, 'message': message, 'timestamp': '2026-10-02T00:00:00Z', 'requestId': 'req-1'}),
    409,
    headers: {'content-type': 'application/json'},
  );
}

http.Response fileBytesResponse() {
  return http.Response.bytes(
    [0x25, 0x50, 0x44, 0x46],
    200,
    headers: {
      'content-type': 'application/pdf',
      'content-disposition': 'inline; filename="notes.pdf"',
    },
  );
}
