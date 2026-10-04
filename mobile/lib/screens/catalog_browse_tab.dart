import 'package:flutter/material.dart';

import '../api/api_exception.dart';
import '../api/catalog_models.dart';
import '../i18n/messages.dart';

typedef CatalogLoader<T> = Future<CatalogPage<T>> Function({String q, String? categoryId});

class CatalogBrowseTab<T> extends StatefulWidget {
  const CatalogBrowseTab({
    required this.messages,
    required this.localeCode,
    required this.load,
    required this.searchKey,
    required this.listKey,
    required this.titleOf,
    required this.subtitleOf,
    this.leadingOf,
    this.onItemTap,
    this.categoryOptions = const [],
    this.categoryFilterKey,
    super.key,
  });

  final Messages messages;
  final String localeCode;
  final CatalogLoader<T> load;
  final Key searchKey;
  final Key listKey;
  final String Function(T item) titleOf;
  final String Function(T item) subtitleOf;
  final Widget? Function(T item)? leadingOf;
  final void Function(T item)? onItemTap;
  final List<CatalogCategoryOption> categoryOptions;
  final Key? categoryFilterKey;

  @override
  State<CatalogBrowseTab<T>> createState() => _CatalogBrowseTabState<T>();
}

class _CatalogBrowseTabState<T> extends State<CatalogBrowseTab<T>> {
  final TextEditingController _query = TextEditingController();
  List<T>? _items;
  String? _error;
  bool _loading = true;
  String? _categoryId;

  @override
  void initState() {
    super.initState();
    _fetch('');
  }

  @override
  void dispose() {
    _query.dispose();
    super.dispose();
  }

  Future<void> _fetch(String q) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final page = await widget.load(q: q, categoryId: _categoryId);
      if (!mounted) {
        return;
      }
      setState(() {
        _items = page.content;
        _loading = false;
      });
    } on ApiException {
      if (!mounted) {
        return;
      }
      setState(() {
        _error = widget.messages.t('catalog.loadFailed');
        _loading = false;
        _items = null;
      });
    }
  }

  void _submitSearch(String value) {
    _fetch(value);
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
          child: Column(
            children: [
              if (widget.categoryOptions.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: DropdownButtonFormField<String?>(
                    key: widget.categoryFilterKey,
                    value: _categoryId,
                    decoration: InputDecoration(
                      labelText: widget.messages.t('catalog.categoryFilter'),
                      border: const OutlineInputBorder(),
                    ),
                    items: [
                      DropdownMenuItem<String?>(
                        value: null,
                        child: Text(widget.messages.t('catalog.allCategories')),
                      ),
                      for (final option in widget.categoryOptions)
                        DropdownMenuItem<String?>(
                          value: option.id,
                          child: Text(option.label),
                        ),
                    ],
                    onChanged: (value) {
                      setState(() => _categoryId = value);
                      _fetch(_query.text.trim());
                    },
                  ),
                ),
              TextField(
                key: widget.searchKey,
                controller: _query,
                textInputAction: TextInputAction.search,
                decoration: InputDecoration(
                  labelText: widget.messages.t('catalog.searchHint'),
                  border: const OutlineInputBorder(),
                ),
                onSubmitted: _submitSearch,
              ),
            ],
          ),
        ),
        const SizedBox(height: 8),
        Expanded(child: _body()),
      ],
    );
  }

  Widget _body() {
    if (_loading) {
      return Center(child: Text(widget.messages.t('catalog.loading')));
    }
    if (_error != null) {
      return Center(
        key: const Key('catalog-error'),
        child: Text(_error!, textAlign: TextAlign.center),
      );
    }
    final items = _items ?? const [];
    if (items.isEmpty) {
      return Center(
        key: const Key('catalog-empty'),
        child: Text(widget.messages.t('catalog.empty')),
      );
    }
    return ListView.builder(
      key: widget.listKey,
      itemCount: items.length,
      itemBuilder: (context, index) {
        final item = items[index];
        return ListTile(
          leading: widget.leadingOf?.call(item),
          title: Text(widget.titleOf(item)),
          subtitle: Text(widget.subtitleOf(item)),
          onTap: widget.onItemTap == null ? null : () => widget.onItemTap!(item),
        );
      },
    );
  }
}

String catalogTitle(String localeCode, String titleEn, String titleBn) {
  return localeCode == 'bn' ? titleBn : titleEn;
}

String catalogSubtitle(String localeCode, String titleEn, String titleBn) {
  return localeCode == 'bn' ? titleEn : titleBn;
}
