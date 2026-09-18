import 'dart:async';
import 'dart:js_interop';
import 'package:web/web.dart' as web;

// A single manually retried image per driver, stored as bytes rather than
// base64 in localStorage. A valid photo may exceed localStorage's small quota.
const _store = 'drafts';

Future<web.IDBDatabase> _open() async {
  final request = web.window.indexedDB.open('gesticollecte_photo_drafts', 1);
  request.onupgradeneeded = ((web.Event event) {
    (request.result as web.IDBDatabase).createObjectStore(_store);
  }).toJS;
  return (await _result(request)) as web.IDBDatabase;
}

Future<JSAny?> _result(web.IDBRequest request) {
  final result = Completer<JSAny?>();
  request.onsuccess = ((web.Event event) {
    result.complete(request.result);
  }).toJS;
  request.onerror = ((web.Event event) {
    result.completeError(const FormatException('Stockage photo indisponible.'));
  }).toJS;
  return result.future;
}

Future<void> _completed(web.IDBTransaction transaction) {
  final result = Completer<void>();
  transaction.oncomplete = ((web.Event event) {
    if (!result.isCompleted) result.complete();
  }).toJS;
  final fail = ((web.Event event) {
    if (!result.isCompleted) {
      result.completeError(
          const FormatException('Photo non enregistrée dans le navigateur.'));
    }
  }).toJS;
  transaction.onerror = fail;
  transaction.onabort = fail;
  return result.future;
}

Future<Map<String, dynamic>?> readBrowserDraft(String key) async {
  final db = await _open();
  try {
    final transaction = db.transaction(_store.toJS, 'readonly');
    final result = await _result(transaction.objectStore(_store).get(key.toJS));
    final data = result.dartify();
    return data is Map ? Map<String, dynamic>.from(data) : null;
  } finally {
    db.close();
  }
}

Future<void> saveBrowserDraft(String key, Map<String, dynamic> draft) async {
  final db = await _open();
  try {
    final transaction = db.transaction(_store.toJS, 'readwrite');
    final completed = _completed(transaction);
    transaction.objectStore(_store).put(draft.jsify(), key.toJS);
    await completed;
  } finally {
    db.close();
  }
}

Future<void> removeBrowserDraft(String key) async {
  final db = await _open();
  try {
    final transaction = db.transaction(_store.toJS, 'readwrite');
    final completed = _completed(transaction);
    transaction.objectStore(_store).delete(key.toJS);
    await completed;
  } finally {
    db.close();
  }
}
