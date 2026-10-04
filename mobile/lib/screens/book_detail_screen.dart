import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../api/api_exception.dart';
import '../api/auth_api.dart';
import '../api/catalog_models.dart';
import '../i18n/locale_controller.dart';
import 'book_order_screen.dart';
import 'catalog_browse_tab.dart';

class BookDetailScreen extends StatefulWidget {
  const BookDetailScreen({required this.api, required this.locale, required this.bookId, super.key});

  final AuthApi api;
  final LocaleController locale;
  final String bookId;

  @override
  State<BookDetailScreen> createState() => _BookDetailScreenState();
}

class _BookDetailScreenState extends State<BookDetailScreen> {
  BookDetail? _book;
  Uint8List? _coverBytes;
  String? _error;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final book = await widget.api.getBook(widget.bookId);
      Uint8List? cover;
      if (book.coverFileId != null) {
        try {
          final file = await widget.api.downloadFile(book.coverFileId!);
          cover = Uint8List.fromList(file.bytes);
        } on ApiException {
          cover = null;
        }
      }
      if (!mounted) {
        return;
      }
      setState(() {
        _book = book;
        _coverBytes = cover;
        _loading = false;
      });
    } on ApiException {
      if (!mounted) {
        return;
      }
      setState(() {
        _error = widget.locale.messages.t('catalog.loadFailed');
        _loading = false;
      });
    }
  }

  void _openOrder() {
    final book = _book!;
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (context) => BookOrderScreen(api: widget.api, messages: widget.locale.messages, book: book),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final messages = widget.locale.messages;
    final localeCode = widget.locale.code;
    return Scaffold(
      key: const Key('book-detail'),
      appBar: AppBar(
        title: Text(_book == null ? messages.t('catalog.tabLibrary') : catalogTitle(localeCode, _book!.titleEn, _book!.titleBn)),
      ),
      body: _loading
          ? Center(child: Text(messages.t('catalog.bookDetailLoading')))
          : _error != null
          ? Center(child: Text(_error!, textAlign: TextAlign.center))
          : _buildBody(localeCode),
    );
  }

  Widget _buildBody(String localeCode) {
    final messages = widget.locale.messages;
    final book = _book!;
    return ListView(
      key: const Key('book-detail-body'),
      padding: const EdgeInsets.all(16),
      children: [
        if (_coverBytes != null)
          Image.memory(_coverBytes!, key: const Key('book-cover'), height: 200, fit: BoxFit.contain)
        else
          const Icon(Icons.menu_book, key: Key('book-cover-placeholder'), size: 120),
        const SizedBox(height: 16),
        Text(catalogTitle(localeCode, book.titleEn, book.titleBn), style: Theme.of(context).textTheme.titleLarge),
        Text(catalogSubtitle(localeCode, book.titleEn, book.titleBn), style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 8),
        Text(book.author, style: Theme.of(context).textTheme.titleSmall),
        const SizedBox(height: 8),
        Text(book.description),
        if (book.price != null) ...[
          const SizedBox(height: 8),
          Text('${messages.t('catalog.price')}: ${book.price}'),
        ],
        if (book.price != null) ...[
          const SizedBox(height: 16),
          FilledButton(
            key: const Key('book-order'),
            onPressed: _openOrder,
            child: Text(messages.t('catalog.orderBook')),
          ),
        ],
      ],
    );
  }
}
