import 'package:flutter/material.dart';

import '../api/api_exception.dart';
import '../api/auth_api.dart';
import '../api/catalog_models.dart';
import '../api/error_text.dart';
import '../i18n/locale_controller.dart';
import '../i18n/locale_toggle.dart';
import '../widgets/catalog_cover.dart';
import 'catalog_browse_tab.dart';
import 'book_detail_screen.dart';
import 'course_detail_screen.dart';
import 'payment_history_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({required this.locale, required this.api, required this.onLogout, super.key});

  final LocaleController locale;
  final AuthApi api;
  final Future<void> Function() onLogout;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  String? _name;
  String? _error;
  bool _loading = true;
  List<CatalogCategoryOption> _courseCategoryOptions = const [];
  List<CatalogCategoryOption> _bookCategoryOptions = const [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final me = await widget.api.me();
      final localeCode = widget.locale.code;
      var courseCategoryOptions = const <CatalogCategoryOption>[];
      var bookCategoryOptions = const <CatalogCategoryOption>[];
      try {
        final courseTree = await widget.api.listCourseCategories();
        courseCategoryOptions = flattenCourseCategories(courseTree, localeCode);
        final bookCategories = await widget.api.listBookCategories();
        bookCategoryOptions = mapBookCategoryOptions(bookCategories, localeCode);
      } on ApiException {
        // Category filters are optional when the catalog category routes are unavailable.
      }
      if (!mounted) {
        return;
      }
      setState(() {
        _name = me.fullName;
        _courseCategoryOptions = courseCategoryOptions;
        _bookCategoryOptions = bookCategoryOptions;
        _loading = false;
      });
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _error = widget.locale.messages.t(errorMessageKey(error.code));
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final messages = widget.locale.messages;
    final localeCode = widget.locale.code;
    return Scaffold(
      key: const Key('home'),
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(messages.t('home.title')),
            if (_name != null && !_loading && _error == null)
              Text(
                _name!,
                key: const Key('home-name'),
                style: Theme.of(context).textTheme.titleMedium,
              ),
          ],
        ),
        actions: [
          LocaleToggle(locale: widget.locale),
          IconButton(
            key: const Key('home-payments'),
            tooltip: messages.t('purchase.openHistory'),
            onPressed: () {
              Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (context) => PaymentHistoryScreen(
                    api: widget.api,
                    messages: messages,
                    localeCode: localeCode,
                  ),
                ),
              );
            },
            icon: const Icon(Icons.receipt_long),
          ),
          TextButton(
            key: const Key('logout'),
            onPressed: () => widget.onLogout(),
            child: Text(messages.t('auth.logout')),
          ),
        ],
      ),
      body: _loading
          ? Center(child: Text(messages.t('home.loading')))
          : _error != null
          ? Center(child: Text(_error!, textAlign: TextAlign.center))
          : DefaultTabController(
              length: 2,
              child: Column(
                children: [
                  TabBar(
                    tabs: [
                      Tab(
                        key: const Key('home-tab-courses'),
                        text: messages.t('catalog.tabCourses'),
                      ),
                      Tab(
                        key: const Key('home-tab-library'),
                        text: messages.t('catalog.tabLibrary'),
                      ),
                    ],
                  ),
                  Expanded(
                    child: TabBarView(
                      children: [
                        CatalogBrowseTab<CourseSummary>(
                          key: const Key('home-courses-pane'),
                          messages: messages,
                          localeCode: localeCode,
                          load: widget.api.listCourses,
                          categoryOptions: _courseCategoryOptions,
                          categoryFilterKey: const Key('catalog-category-courses'),
                          searchKey: const Key('catalog-search-courses'),
                          listKey: const Key('catalog-course-list'),
                          titleOf: (course) => catalogTitle(localeCode, course.titleEn, course.titleBn),
                          subtitleOf: (course) => catalogSubtitle(localeCode, course.titleEn, course.titleBn),
                          leadingOf: (course) {
                            if (!CatalogCover.shouldShow(course.coverFileId)) {
                              return null;
                            }
                            return SizedBox(
                              width: 56,
                              height: 56,
                              child: CatalogCover(
                                api: widget.api,
                                messages: messages,
                                coverFileId: course.coverFileId!,
                                width: 56,
                                height: 56,
                                imageKey: Key('course-list-cover-${course.id}'),
                                loadingKey: Key('course-list-cover-loading-${course.id}'),
                              ),
                            );
                          },
                          onItemTap: (course) {
                            Navigator.of(context).push(
                              MaterialPageRoute<void>(
                                builder: (context) => CourseDetailScreen(
                                  api: widget.api,
                                  locale: widget.locale,
                                  courseId: course.id,
                                ),
                              ),
                            );
                          },
                        ),
                        CatalogBrowseTab<BookSummary>(
                          key: const Key('home-library-pane'),
                          messages: messages,
                          localeCode: localeCode,
                          load: widget.api.listBooks,
                          categoryOptions: _bookCategoryOptions,
                          categoryFilterKey: const Key('catalog-category-books'),
                          searchKey: const Key('catalog-search-books'),
                          listKey: const Key('catalog-book-list'),
                          titleOf: (book) => catalogTitle(localeCode, book.titleEn, book.titleBn),
                          subtitleOf: (book) => book.author,
                          onItemTap: (book) {
                            Navigator.of(context).push(
                              MaterialPageRoute<void>(
                                builder: (context) => BookDetailScreen(
                                  api: widget.api,
                                  locale: widget.locale,
                                  bookId: book.id,
                                ),
                              ),
                            );
                          },
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
    );
  }
}
