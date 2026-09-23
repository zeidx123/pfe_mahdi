import 'dart:async';
import 'dart:js_interop';
import 'dart:typed_data';
import 'package:web/web.dart' as web;

Future<void> openSourceFile(
    Uint8List bytes, String filename, String mimeType) async {
  final blob = web.Blob(
      <JSAny>[bytes.toJS].toJS, web.BlobPropertyBag(type: mimeType));
  final objectUrl = web.URL.createObjectURL(blob);
  final anchor = web.HTMLAnchorElement()
    ..href = objectUrl
    ..download = filename
    ..style.display = 'none';
  try {
    final body = web.document.body;
    if (body == null) {
      throw const FormatException(
          'Le téléchargement n’a pas pu démarrer. Réessayez.');
    }
    body.appendChild(anchor);
    anchor.click();
  } finally {
    anchor.remove();
    // Keep the object URL alive while the browser starts reading the download.
    Timer(const Duration(seconds: 30), () => web.URL.revokeObjectURL(objectUrl));
  }
}
