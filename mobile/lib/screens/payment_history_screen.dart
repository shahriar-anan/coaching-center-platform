import 'package:flutter/material.dart';

import '../api/api_exception.dart';
import '../api/auth_api.dart';
import '../api/catalog_models.dart';
import '../i18n/messages.dart';
import 'catalog_browse_tab.dart';

String paymentStatusText(Messages messages, String status) {
  switch (status) {
    case 'PENDING':
      return messages.t('purchase.statusPending');
    case 'SUCCESS':
      return messages.t('purchase.statusSuccess');
    case 'FAILED':
      return messages.t('purchase.statusFailed');
    default:
      return status;
  }
}

String paymentMethodText(Messages messages, String method) {
  switch (method) {
    case 'BKASH':
      return messages.t('purchase.methodBkash');
    case 'NAGAD':
      return messages.t('purchase.methodNagad');
    default:
      return method;
  }
}

class PaymentHistoryTile extends StatelessWidget {
  const PaymentHistoryTile({
    required this.row,
    required this.messages,
    required this.tileKey,
    this.courseTitle,
    super.key,
  });

  final CoursePurchaseView row;
  final Messages messages;
  final Key tileKey;
  final String? courseTitle;

  @override
  Widget build(BuildContext context) {
    final title = courseTitle ?? messages.t('purchase.coursePayment');
    final method = paymentMethodText(messages, row.method);
    final status = paymentStatusText(messages, row.paymentStatus);
    return ListTile(
      key: tileKey,
      title: Text(title),
      isThreeLine: true,
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('$method · ${row.amount}'),
          Text(row.transactionRef),
          Text(status),
        ],
      ),
    );
  }
}

class PaymentHistoryScreen extends StatefulWidget {
  const PaymentHistoryScreen({
    required this.api,
    required this.messages,
    required this.localeCode,
    super.key,
  });

  final AuthApi api;
  final Messages messages;
  final String localeCode;

  @override
  State<PaymentHistoryScreen> createState() => _PaymentHistoryScreenState();
}

class _PaymentHistoryScreenState extends State<PaymentHistoryScreen> {
  List<CoursePurchaseView> _rows = const [];
  Map<String, String> _titles = const {};
  String? _error;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final rows = await widget.api.listMyPurchases();
      final titles = await _courseTitles(rows);
      if (!mounted) {
        return;
      }
      setState(() {
        _rows = rows;
        _titles = titles;
        _loading = false;
      });
    } on ApiException {
      if (!mounted) {
        return;
      }
      setState(() {
        _error = widget.messages.t('purchase.historyLoadFailed');
        _loading = false;
      });
    }
  }

  Future<Map<String, String>> _courseTitles(List<CoursePurchaseView> rows) async {
    final titles = <String, String>{};
    final ids = rows.map((row) => row.courseId).toSet();
    await Future.wait(ids.map((id) async {
      try {
        final course = await widget.api.getCourse(id);
        titles[id] = catalogTitle(widget.localeCode, course.titleEn, course.titleBn);
      } catch (_) {
        // A closed course is absent from the catalog. The payment row still shows.
      }
    }));
    return titles;
  }

  @override
  Widget build(BuildContext context) {
    final messages = widget.messages;
    return Scaffold(
      key: const Key('payment-history'),
      appBar: AppBar(title: Text(messages.t('purchase.history'))),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
          ? Center(child: Text(_error!, key: const Key('payment-history-error'), textAlign: TextAlign.center))
          : _rows.isEmpty
          ? Center(child: Text(messages.t('purchase.emptyHistory')))
          : ListView(
              key: const Key('payment-history-list'),
              children: [
                for (final row in _rows)
                  PaymentHistoryTile(
                    row: row,
                    messages: messages,
                    courseTitle: _titles[row.courseId],
                    tileKey: Key('payment-history-${row.enrollmentId}'),
                  ),
              ],
            ),
    );
  }
}
