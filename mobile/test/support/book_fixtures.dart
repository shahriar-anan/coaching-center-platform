import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:mobile/api/catalog_models.dart';

const bookId = 'book-1';
const bookCoverFileId = 'cover-1';

Map<String, dynamic> bookSummaryJson({required String id, required String titleEn}) {
  return {
    'id': id,
    'titleEn': titleEn,
    'titleBn': 'বই',
    'author': 'Author Name',
    'price': 80,
    'coverFileId': bookCoverFileId,
  };
}

http.Response bookDetailResponse({
  required String id,
  String? coverFileId = bookCoverFileId,
}) {
  return http.Response(
    jsonEncode({
      'id': id,
      'titleEn': 'Sample Book',
      'titleBn': 'নমুনা বই',
      'author': 'Author Name',
      'description': 'Book description text',
      'price': 80,
      'coverFileId': coverFileId,
    }),
    200,
    headers: {'content-type': 'application/json'},
  );
}

BookDetail bookForOrder() {
  return const BookDetail(
    id: bookId,
    titleEn: 'Sample Book',
    titleBn: 'নমুনা বই',
    author: 'Author Name',
    description: 'Book description text',
    price: 80,
    coverFileId: bookCoverFileId,
  );
}

http.Response bookOrderCreated({
  String orderId = 'order-1',
  String paymentNumber = '01700000000',
}) {
  return http.Response(
    jsonEncode({
      'orderId': orderId,
      'status': 'PLACED',
      'fulfillment': 'DELIVERY',
      'paymentMethod': 'ONLINE',
      'paymentId': 'payment-1',
      'paymentStatus': 'PENDING',
      'paymentNumber': paymentNumber,
    }),
    201,
    headers: {'content-type': 'application/json'},
  );
}

http.Response bookOrderHistoryList({required String orderId, String status = 'PLACED'}) {
  return http.Response(
    jsonEncode([
      {
        'orderId': orderId,
        'bookId': bookId,
        'status': status,
        'fulfillment': 'DELIVERY',
        'paymentMethod': 'ONLINE',
        'phone': '+8801711111111',
        'address': 'House 1',
        'area': 'Dhaka',
      },
    ]),
    200,
    headers: {'content-type': 'application/json'},
  );
}

http.Response tinyPngBytes() {
  return http.Response.bytes(
    [
      0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52, 0x00, 0x00,
      0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x06, 0x00, 0x00, 0x00, 0x1f, 0x15, 0xc4, 0x89, 0x00, 0x00, 0x00,
      0x0a, 0x49, 0x44, 0x41, 0x54, 0x78, 0x9c, 0x63, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00, 0x01, 0x0d, 0x0a, 0x2d, 0xb4,
      0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4e, 0x44, 0xae, 0x42, 0x60, 0x82,
    ],
    200,
    headers: {
      'content-type': 'image/png',
      'content-disposition': 'inline; filename="cover.png"',
    },
  );
}
