import 'package:flutter/material.dart';

import '../api/api_exception.dart';
import '../api/auth_api.dart';
import '../api/catalog_models.dart';
import '../api/error_text.dart';
import '../i18n/messages.dart';

class BookOrderScreen extends StatefulWidget {
  const BookOrderScreen({required this.api, required this.messages, required this.book, super.key});

  final AuthApi api;
  final Messages messages;
  final BookDetail book;

  @override
  State<BookOrderScreen> createState() => _BookOrderScreenState();
}

class _BookOrderScreenState extends State<BookOrderScreen> {
  final TextEditingController _phone = TextEditingController();
  final TextEditingController _address = TextEditingController();
  final TextEditingController _area = TextEditingController();
  final TextEditingController _transactionRef = TextEditingController();
  String? _fulfillment;
  String? _paymentMethod;
  String _method = 'BKASH';
  String? _error;
  String? _paymentNumber;
  bool _busy = false;
  List<BookOrderView> _history = const [];
  bool _historyLoading = true;

  bool get _online => _paymentMethod == 'ONLINE';
  bool get _delivery => _fulfillment == 'DELIVERY';

  @override
  void initState() {
    super.initState();
    _loadHistory();
  }

  @override
  void dispose() {
    _phone.dispose();
    _address.dispose();
    _area.dispose();
    _transactionRef.dispose();
    super.dispose();
  }

  Future<void> _loadHistory() async {
    try {
      final rows = await widget.api.listMyOrders();
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

  bool get _invalidCashDelivery => _delivery && _paymentMethod == 'CASH';

  bool get _choicesReady => _fulfillment != null && _paymentMethod != null;

  Future<void> _submit() async {
    if (_busy || !_choicesReady || _invalidCashDelivery) {
      return;
    }
    final phone = _phone.text.trim();
    if (phone.isEmpty) {
      return;
    }
    if (_delivery) {
      final address = _address.text.trim();
      if (address.isEmpty) {
        return;
      }
    }
    if (_online) {
      final transactionRef = _transactionRef.text.trim();
      if (transactionRef.isEmpty) {
        return;
      }
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final address = _address.text.trim();
      final area = _area.text.trim();
      final result = await widget.api.placeBookOrder(
        bookId: widget.book.id,
        fulfillment: _fulfillment!,
        paymentMethod: _paymentMethod!,
        phone: phone,
        address: _delivery ? address : null,
        area: _delivery && area.isNotEmpty ? area : null,
        method: _online ? _method : null,
        transactionRef: _online ? _transactionRef.text.trim() : null,
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
      key: const Key('book-order-screen'),
      appBar: AppBar(title: Text(messages.t('bookOrder.title'))),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          if (_error != null) ...[
            Text(_error!, key: const Key('book-order-error'), style: TextStyle(color: Theme.of(context).colorScheme.error)),
            const SizedBox(height: 12),
          ],
          Text(messages.t('bookOrder.fulfillment'), style: Theme.of(context).textTheme.titleSmall),
          const SizedBox(height: 8),
          SegmentedButton<String>(
            key: const Key('book-order-fulfillment'),
            emptySelectionAllowed: true,
            segments: [
              ButtonSegment(value: 'PICKUP', label: Text(messages.t('bookOrder.pickup'))),
              ButtonSegment(value: 'DELIVERY', label: Text(messages.t('bookOrder.delivery'))),
            ],
            selected: _fulfillment == null ? const {} : {_fulfillment!},
            onSelectionChanged: _busy ? null : (selection) => setState(() => _fulfillment = selection.first),
          ),
          const SizedBox(height: 16),
          Text(messages.t('bookOrder.payment'), style: Theme.of(context).textTheme.titleSmall),
          const SizedBox(height: 8),
          SegmentedButton<String>(
            key: const Key('book-order-payment'),
            emptySelectionAllowed: true,
            segments: [
              ButtonSegment(value: 'ONLINE', label: Text(messages.t('bookOrder.paymentOnline'))),
              ButtonSegment(value: 'CASH', label: Text(messages.t('bookOrder.paymentCash'))),
            ],
            selected: _paymentMethod == null ? const {} : {_paymentMethod!},
            onSelectionChanged: _busy ? null : (selection) => setState(() => _paymentMethod = selection.first),
          ),
          if (_invalidCashDelivery) ...[
            const SizedBox(height: 12),
            Text(
              messages.t('bookOrder.cashDeliveryInvalid'),
              key: const Key('book-order-cash-delivery-error'),
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          ],
          if (_choicesReady && !_invalidCashDelivery) ...[
          const SizedBox(height: 16),
          TextField(
            key: const Key('book-order-phone'),
            controller: _phone,
            decoration: InputDecoration(labelText: messages.t('bookOrder.phone')),
            enabled: !_busy,
          ),
          if (_delivery) ...[
            const SizedBox(height: 16),
            TextField(
              key: const Key('book-order-address'),
              controller: _address,
              decoration: InputDecoration(labelText: messages.t('bookOrder.address')),
              enabled: !_busy,
            ),
            const SizedBox(height: 16),
            TextField(
              key: const Key('book-order-area'),
              controller: _area,
              decoration: InputDecoration(labelText: messages.t('bookOrder.area')),
              enabled: !_busy,
            ),
          ],
          if (_online) ...[
            const SizedBox(height: 16),
            SegmentedButton<String>(
              key: const Key('book-order-method'),
              segments: [
                ButtonSegment(value: 'BKASH', label: Text(messages.t('purchase.methodBkash'))),
                ButtonSegment(value: 'NAGAD', label: Text(messages.t('purchase.methodNagad'))),
              ],
              selected: {_method},
              onSelectionChanged: _busy ? null : (selection) => setState(() => _method = selection.first),
            ),
            const SizedBox(height: 16),
            TextField(
              key: const Key('book-order-transaction-ref'),
              controller: _transactionRef,
              decoration: InputDecoration(labelText: messages.t('purchase.transactionRef')),
              enabled: !_busy,
            ),
          ],
          const SizedBox(height: 24),
          FilledButton(
            key: const Key('book-order-submit'),
            onPressed: _busy || !_choicesReady || _invalidCashDelivery ? null : _submit,
            child: Text(messages.t('bookOrder.submit')),
          ),
          ],
          if (_paymentNumber != null) ...[
            const SizedBox(height: 24),
            Text(messages.t('bookOrder.paymentNumber'), style: Theme.of(context).textTheme.titleSmall),
            Text(_paymentNumber!, key: const Key('book-order-payment-number'), style: Theme.of(context).textTheme.titleLarge),
          ],
          const SizedBox(height: 24),
          Text(messages.t('bookOrder.history'), style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          if (_historyLoading)
            const Center(child: CircularProgressIndicator())
          else if (_history.isEmpty)
            Text(messages.t('bookOrder.emptyHistory'))
          else
            ..._history.map(
              (row) => ListTile(
                key: Key('book-order-history-${row.orderId}'),
                title: Text('${messages.t('bookOrder.status')}: ${row.status}'),
                subtitle: Text('${row.fulfillment} · ${row.paymentMethod}'),
              ),
            ),
        ],
      ),
    );
  }
}
