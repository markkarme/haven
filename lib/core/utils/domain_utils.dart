/// Domain normalization and validation for website block rules.
abstract final class DomainUtils {
  static final RegExp _pattern = RegExp(
    r'^(\*\.)?([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,}$',
    caseSensitive: false,
  );

  static String normalize(String raw) {
    var value = raw.trim().toLowerCase();
    value = value.replaceFirst(RegExp(r'^https?://'), '');
    value = value.split('/').first;
    value = value.split('?').first;
    value = value.split(':').first;
    if (value.startsWith('www.') && !value.startsWith('*.')) {
      // Keep as-is for exact www rules; users can add bare domain separately.
    }
    if (value.endsWith('.')) {
      value = value.substring(0, value.length - 1);
    }
    return value;
  }

  static bool isValid(String domain) => _pattern.hasMatch(domain);

  static String? validate(String? raw) {
    final normalized = normalize(raw ?? '');
    if (normalized.isEmpty) return 'Enter a domain';
    if (!isValid(normalized)) {
      return 'Use a valid domain like example.com or *.example.com';
    }
    return null;
  }
}
