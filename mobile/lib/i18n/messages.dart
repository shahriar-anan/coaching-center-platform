class Messages {
  const Messages(this.code, this._values);

  final String code;
  final Map<String, String> _values;

  Iterable<String> get keys => _values.keys;

  String t(String key) {
    final value = _values[key];
    if (value == null) {
      throw StateError('Missing catalog string: $key');
    }
    return value;
  }
}
