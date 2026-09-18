Future<Map<String, dynamic>?> readBrowserDraft(String key) async => null;

Future<void> saveBrowserDraft(String key, Map<String, dynamic> draft) async =>
    throw UnsupportedError('Browser draft storage is only available on web.');

Future<void> removeBrowserDraft(String key) async {}
