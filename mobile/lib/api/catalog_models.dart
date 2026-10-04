class CourseCategoryNode {
  const CourseCategoryNode({
    required this.id,
    required this.titleEn,
    required this.titleBn,
    this.children = const [],
  });

  final String id;
  final String titleEn;
  final String titleBn;
  final List<CourseCategoryNode> children;

  factory CourseCategoryNode.fromJson(Map<String, dynamic> json) {
    final children = json['children'] as List<dynamic>? ?? const [];
    return CourseCategoryNode(
      id: json['id'] as String,
      titleEn: json['titleEn'] as String,
      titleBn: json['titleBn'] as String,
      children: children
          .map((item) => CourseCategoryNode.fromJson(Map<String, dynamic>.from(item as Map)))
          .toList(),
    );
  }
}

class BookCategorySummary {
  const BookCategorySummary({required this.id, required this.titleEn, required this.titleBn});

  final String id;
  final String titleEn;
  final String titleBn;

  factory BookCategorySummary.fromJson(Map<String, dynamic> json) {
    return BookCategorySummary(
      id: json['id'] as String,
      titleEn: json['titleEn'] as String,
      titleBn: json['titleBn'] as String,
    );
  }
}

List<CatalogCategoryOption> flattenCourseCategories(
  List<CourseCategoryNode> nodes,
  String localeCode, {
  String prefix = '',
}) {
  final out = <CatalogCategoryOption>[];
  for (final node in nodes) {
    final title = _localizedTitle(localeCode, node.titleEn, node.titleBn);
    final path = prefix.isEmpty ? title : '$prefix › $title';
    out.add(CatalogCategoryOption(id: node.id, label: path));
    out.addAll(flattenCourseCategories(node.children, localeCode, prefix: path));
  }
  return out;
}

List<CatalogCategoryOption> mapBookCategoryOptions(List<BookCategorySummary> categories, String localeCode) {
  return categories
      .map((category) => CatalogCategoryOption(
            id: category.id,
            label: _localizedTitle(localeCode, category.titleEn, category.titleBn),
          ))
      .toList();
}

class CatalogCategoryOption {
  const CatalogCategoryOption({required this.id, required this.label});

  final String id;
  final String label;
}

String _localizedTitle(String localeCode, String titleEn, String titleBn) {
  return localeCode == 'bn' ? titleBn : titleEn;
}

class MyCourseSummary {
  const MyCourseSummary({required this.courseId, required this.titleEn, required this.titleBn});

  final String courseId;
  final String titleEn;
  final String titleBn;

  factory MyCourseSummary.fromJson(Map<String, dynamic> json) {
    return MyCourseSummary(
      courseId: json['courseId'] as String,
      titleEn: json['titleEn'] as String,
      titleBn: json['titleBn'] as String,
    );
  }
}

class CatalogPage<T> {
  const CatalogPage({required this.content});

  final List<T> content;
}

class CourseSummary {
  const CourseSummary({
    required this.id,
    required this.titleEn,
    required this.titleBn,
    this.price,
    required this.deliveryMode,
    required this.accessType,
    this.coverFileId,
  });

  final String id;
  final String titleEn;
  final String titleBn;
  final num? price;
  final String deliveryMode;
  final String accessType;
  final String? coverFileId;

  factory CourseSummary.fromJson(Map<String, dynamic> json) {
    return CourseSummary(
      id: json['id'] as String,
      titleEn: json['titleEn'] as String,
      titleBn: json['titleBn'] as String,
      price: json['price'] as num?,
      deliveryMode: json['deliveryMode'] as String,
      accessType: json['accessType'] as String,
      coverFileId: json['coverFileId'] as String?,
    );
  }
}

class CatalogNode {
  const CatalogNode({
    required this.id,
    this.parentId,
    required this.nodeType,
    required this.title,
    required this.position,
    required this.free,
  });

  final String id;
  final String? parentId;
  final String nodeType;
  final String title;
  final int position;
  final bool free;

  factory CatalogNode.fromJson(Map<String, dynamic> json) {
    return CatalogNode(
      id: json['id'] as String,
      parentId: json['parentId'] as String?,
      nodeType: json['nodeType'] as String,
      title: json['title'] as String,
      position: json['position'] as int,
      free: json['free'] as bool,
    );
  }
}

class CategoryPathItem {
  const CategoryPathItem({required this.titleEn, required this.titleBn});

  final String titleEn;
  final String titleBn;

  factory CategoryPathItem.fromJson(Map<String, dynamic> json) {
    return CategoryPathItem(titleEn: json['titleEn'] as String, titleBn: json['titleBn'] as String);
  }
}

class CourseMaterialSummary {
  const CourseMaterialSummary({
    required this.id,
    required this.originalFileName,
    required this.contentType,
    required this.byteSize,
    required this.free,
  });

  final String id;
  final String originalFileName;
  final String contentType;
  final int byteSize;
  final bool free;

  factory CourseMaterialSummary.fromJson(Map<String, dynamic> json) {
    return CourseMaterialSummary(
      id: json['id'] as String,
      originalFileName: json['originalFileName'] as String,
      contentType: json['contentType'] as String,
      byteSize: json['byteSize'] as int,
      free: json['free'] as bool,
    );
  }
}

class CourseDetail {
  const CourseDetail({
    required this.id,
    required this.titleEn,
    required this.titleBn,
    required this.description,
    required this.contentLanguage,
    this.price,
    required this.deliveryMode,
    required this.accessType,
    this.coverFileId,
    required this.contentTree,
    this.categoryPath = const [],
    this.courseMaterials = const [],
  });

  final String id;
  final String titleEn;
  final String titleBn;
  final String description;
  final String contentLanguage;
  final num? price;
  final String deliveryMode;
  final String accessType;
  final String? coverFileId;
  final List<CatalogNode> contentTree;
  final List<CategoryPathItem> categoryPath;
  final List<CourseMaterialSummary> courseMaterials;

  factory CourseDetail.fromJson(Map<String, dynamic> json) {
    final tree = json['contentTree'] as List<dynamic>? ?? const [];
    final path = json['categoryPath'] as List<dynamic>? ?? const [];
    final materials = json['courseMaterials'] as List<dynamic>? ?? const [];
    return CourseDetail(
      id: json['id'] as String,
      titleEn: json['titleEn'] as String,
      titleBn: json['titleBn'] as String,
      description: json['description'] as String,
      contentLanguage: json['contentLanguage'] as String,
      price: json['price'] as num?,
      deliveryMode: json['deliveryMode'] as String,
      accessType: json['accessType'] as String,
      coverFileId: json['coverFileId'] as String?,
      contentTree: tree.map((node) => CatalogNode.fromJson(Map<String, dynamic>.from(node as Map))).toList(),
      categoryPath: path.map((item) => CategoryPathItem.fromJson(Map<String, dynamic>.from(item as Map))).toList(),
      courseMaterials: materials
          .map((item) => CourseMaterialSummary.fromJson(Map<String, dynamic>.from(item as Map)))
          .toList(),
    );
  }
}

class LectureMaterialSummary {
  const LectureMaterialSummary({
    required this.id,
    required this.originalFileName,
    required this.contentType,
    required this.byteSize,
  });

  final String id;
  final String originalFileName;
  final String contentType;
  final int byteSize;

  factory LectureMaterialSummary.fromJson(Map<String, dynamic> json) {
    return LectureMaterialSummary(
      id: json['id'] as String,
      originalFileName: json['originalFileName'] as String,
      contentType: json['contentType'] as String,
      byteSize: json['byteSize'] as int,
    );
  }
}

class LectureContent {
  const LectureContent({
    required this.id,
    required this.title,
    required this.videoRef,
    required this.notes,
    required this.materialIds,
    this.materials = const [],
  });

  final String id;
  final String title;
  final String? videoRef;
  final String? notes;
  final List<String> materialIds;
  final List<LectureMaterialSummary> materials;

  factory LectureContent.fromJson(Map<String, dynamic> json) {
    final materials = json['materials'] as List<dynamic>? ?? const [];
    final materialIds = json['materialIds'] as List<dynamic>? ?? const [];
    return LectureContent(
      id: json['id'] as String,
      title: json['title'] as String,
      videoRef: json['videoRef'] as String?,
      notes: json['notes'] as String?,
      materialIds: materialIds.map((id) => id as String).toList(),
      materials: materials.map((item) => LectureMaterialSummary.fromJson(Map<String, dynamic>.from(item as Map))).toList(),
    );
  }
}

class FileDownload {
  const FileDownload({required this.bytes, required this.contentType, required this.fileName});

  final List<int> bytes;
  final String contentType;
  final String fileName;
}

class CoursePurchaseResult {
  const CoursePurchaseResult({
    required this.enrollmentId,
    required this.paymentId,
    required this.enrollmentStatus,
    required this.paymentStatus,
    required this.amount,
    required this.method,
    required this.transactionRef,
    required this.paymentNumber,
  });

  final String enrollmentId;
  final String paymentId;
  final String enrollmentStatus;
  final String paymentStatus;
  final num amount;
  final String method;
  final String transactionRef;
  final String paymentNumber;

  factory CoursePurchaseResult.fromJson(Map<String, dynamic> json) {
    return CoursePurchaseResult(
      enrollmentId: json['enrollmentId'] as String,
      paymentId: json['paymentId'] as String,
      enrollmentStatus: json['enrollmentStatus'] as String,
      paymentStatus: json['paymentStatus'] as String,
      amount: json['amount'] as num,
      method: json['method'] as String,
      transactionRef: json['transactionRef'] as String,
      paymentNumber: json['paymentNumber'] as String,
    );
  }
}

class CoursePurchaseView {
  const CoursePurchaseView({
    required this.enrollmentId,
    required this.courseId,
    required this.enrollmentStatus,
    required this.paymentStatus,
    required this.amount,
    required this.method,
    required this.transactionRef,
  });

  final String enrollmentId;
  final String courseId;
  final String enrollmentStatus;
  final String paymentStatus;
  final num amount;
  final String method;
  final String transactionRef;

  factory CoursePurchaseView.fromJson(Map<String, dynamic> json) {
    return CoursePurchaseView(
      enrollmentId: json['enrollmentId'] as String,
      courseId: json['courseId'] as String,
      enrollmentStatus: json['enrollmentStatus'] as String,
      paymentStatus: json['paymentStatus'] as String,
      amount: json['amount'] as num,
      method: json['method'] as String,
      transactionRef: json['transactionRef'] as String,
    );
  }
}

class BookOrderResult {
  const BookOrderResult({
    required this.orderId,
    required this.status,
    required this.fulfillment,
    required this.paymentMethod,
    this.paymentId,
    this.paymentStatus,
    this.paymentNumber,
  });

  final String orderId;
  final String status;
  final String fulfillment;
  final String paymentMethod;
  final String? paymentId;
  final String? paymentStatus;
  final String? paymentNumber;

  factory BookOrderResult.fromJson(Map<String, dynamic> json) {
    return BookOrderResult(
      orderId: json['orderId'] as String,
      status: json['status'] as String,
      fulfillment: json['fulfillment'] as String,
      paymentMethod: json['paymentMethod'] as String,
      paymentId: json['paymentId'] as String?,
      paymentStatus: json['paymentStatus'] as String?,
      paymentNumber: json['paymentNumber'] as String?,
    );
  }
}

class BookOrderView {
  const BookOrderView({
    required this.orderId,
    required this.bookId,
    required this.status,
    required this.fulfillment,
    required this.paymentMethod,
    required this.phone,
    this.address,
    this.area,
  });

  final String orderId;
  final String bookId;
  final String status;
  final String fulfillment;
  final String paymentMethod;
  final String phone;
  final String? address;
  final String? area;

  factory BookOrderView.fromJson(Map<String, dynamic> json) {
    return BookOrderView(
      orderId: json['orderId'] as String,
      bookId: json['bookId'] as String,
      status: json['status'] as String,
      fulfillment: json['fulfillment'] as String,
      paymentMethod: json['paymentMethod'] as String,
      phone: json['phone'] as String,
      address: json['address'] as String?,
      area: json['area'] as String?,
    );
  }
}

class BookSummary {
  const BookSummary({
    required this.id,
    required this.titleEn,
    required this.titleBn,
    required this.author,
    this.price,
    this.coverFileId,
  });

  final String id;
  final String titleEn;
  final String titleBn;
  final String author;
  final num? price;
  final String? coverFileId;

  factory BookSummary.fromJson(Map<String, dynamic> json) {
    return BookSummary(
      id: json['id'] as String,
      titleEn: json['titleEn'] as String,
      titleBn: json['titleBn'] as String,
      author: json['author'] as String,
      price: json['price'] as num?,
      coverFileId: json['coverFileId'] as String?,
    );
  }
}

class BookDetail {
  const BookDetail({
    required this.id,
    required this.titleEn,
    required this.titleBn,
    required this.author,
    required this.description,
    this.price,
    this.coverFileId,
  });

  final String id;
  final String titleEn;
  final String titleBn;
  final String author;
  final String description;
  final num? price;
  final String? coverFileId;

  factory BookDetail.fromJson(Map<String, dynamic> json) {
    return BookDetail(
      id: json['id'] as String,
      titleEn: json['titleEn'] as String,
      titleBn: json['titleBn'] as String,
      author: json['author'] as String,
      description: json['description'] as String,
      price: json['price'] as num?,
      coverFileId: json['coverFileId'] as String?,
    );
  }
}
