import 'package:flutter/material.dart';

import '../api/api_exception.dart';
import '../api/auth_api.dart';
import '../api/catalog_models.dart';
import '../api/error_text.dart';
import '../i18n/messages.dart';
import 'payment_history_screen.dart';

class CoursePurchaseScreen extends StatefulWidget {
  const CoursePurchaseScreen({required this.api, required this.messages, required this.course, super.key});

  final AuthApi api;
  final Messages messages;
  final CourseDetail course;

  @override
  State<CoursePurchaseScreen> createState() => _CoursePurchaseScreenState();
}

class _CoursePurchaseScreenState extends State<CoursePurchaseScreen> {
  final TextEditingController _transactionRef = TextEditingController();
  String _method = 'BKASH';
  String _deliveryMode = 'ONLINE';
  String? _error;
  String? _paymentNumber;
  bool _busy = false;
  List<CoursePurchaseView> _history = const [];
  bool _historyLoading = true;

  bool get _hybrid => widget.course.deliveryMode == 'HYBRID';

  @override
  void initState() {
    super.initState();
    _loadHistory();
  }

  @override
  void dispose() {
    _transactionRef.dispose();
    super.dispose();
  }

  Future<void> _loadHistory() async {
    try {
      final rows = await widget.api.listMyPurchases();
      if (!mounted) {
        return;
      }
      setState(() {
        _history = rows;
        _historyLoading = false;
      });
    } on ApiException {
      if (!mounted) {
        return;
      }
      setState(() => _historyLoading = false);
    }
  }

  Future<void> _submit() async {
    if (_busy) {
      return;
    }
    final transactionRef = _transactionRef.text.trim();
    if (transactionRef.isEmpty) {
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final result = await widget.api.purchaseCourse(
        courseId: widget.course.id,
        method: _method,
        transactionRef: transactionRef,
        deliveryMode: _hybrid ? _deliveryMode : null,
      );
      if (!mounted) {
        return;
      }
      setState(() {
        _paymentNumber = result.paymentNumber;
        _busy = false;
      });
      await _loadHistory();
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _error = widget.messages.t(errorMessageKey(error.code));
        _busy = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final messages = widget.messages;
    return Scaffold(
      key: const Key('course-purchase'),
      appBar: AppBar(title: Text(messages.t('purchase.title'))),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(messages.t('purchase.history'), style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          if (_historyLoading)
            const Center(child: CircularProgressIndicator())
          else if (_history.isEmpty)
            Text(messages.t('purchase.emptyHistory'))
          else
            ..._history.map(
              (row) => PaymentHistoryTile(
                row: row,
                messages: messages,
                tileKey: Key('purchase-history-${row.enrollmentId}'),
              ),
            ),
          const SizedBox(height: 24),
          if (_error != null) ...[
            Text(_error!, key: const Key('purchase-error'), style: TextStyle(color: Theme.of(context).colorScheme.error)),
            const SizedBox(height: 12),
          ],
          Text(messages.t('purchase.method'), style: Theme.of(context).textTheme.titleSmall),
          const SizedBox(height: 8),
          SegmentedButton<String>(
            key: const Key('purchase-method'),
            segments: [
              ButtonSegment(value: 'BKASH', label: Text(messages.t('purchase.methodBkash'))),
              ButtonSegment(value: 'NAGAD', label: Text(messages.t('purchase.methodNagad'))),
            ],
            selected: {_method},
            onSelectionChanged: _busy ? null : (selection) => setState(() => _method = selection.first),
          ),
          const SizedBox(height: 16),
          TextField(
            key: const Key('purchase-transaction-ref'),
            controller: _transactionRef,
            decoration: InputDecoration(labelText: messages.t('purchase.transactionRef')),
            enabled: !_busy,
          ),
          if (_hybrid) ...[
            const SizedBox(height: 16),
            Text(messages.t('purchase.deliveryChoice'), style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: 8),
            SegmentedButton<String>(
              key: const Key('purchase-delivery-mode'),
              segments: [
                ButtonSegment(value: 'ONLINE', label: Text(messages.t('purchase.deliveryOnline'))),
                ButtonSegment(value: 'OFFLINE', label: Text(messages.t('purchase.deliveryOffline'))),
              ],
              selected: {_deliveryMode},
              onSelectionChanged: _busy ? null : (selection) => setState(() => _deliveryMode = selection.first),
            ),
          ],
          const SizedBox(height: 24),
          FilledButton(
            key: const Key('purchase-submit'),
            onPressed: _busy ? null : _submit,
            child: Text(messages.t('purchase.submit')),
          ),
          if (_paymentNumber != null) ...[
            const SizedBox(height: 24),
            Text(messages.t('purchase.paymentNumber'), style: Theme.of(context).textTheme.titleSmall),
            Text(_paymentNumber!, key: const Key('purchase-payment-number'), style: Theme.of(context).textTheme.titleLarge),
          ],
        ],
      ),
    );
  }
}
