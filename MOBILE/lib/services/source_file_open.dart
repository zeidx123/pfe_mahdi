import 'dart:typed_data';
import 'source_file_open_stub.dart'
    if (dart.library.io) 'source_file_open_io.dart'
    if (dart.library.html) 'source_file_open_web.dart' as platform;

/// Presents bytes already fetched through the authenticated document service.
/// No remote URL or authentication token is passed to the platform viewer.
Future<void> openSourceFile(
    Uint8List bytes, String filename, String mimeType) async {
  if (bytes.isEmpty) {
    throw const FormatException('Le document téléchargé est vide.');
  }
  final basename = filename.replaceAll('\\', '/').split('/').last;
  var safeName = basename
      .replaceAll(RegExp(r'[^A-Za-z0-9._ -]'), '_')
      .replaceAll(RegExp(r'^[. ]+|[. ]+$'), '');
  if (safeName.isEmpty) safeName = 'document';
  if (safeName.length > 128) {
    final extensionIndex = safeName.lastIndexOf('.');
    final extension = extensionIndex > 0 &&
            safeName.length - extensionIndex <= 12
        ? safeName.substring(extensionIndex)
        : '';
    safeName = '${safeName.substring(0, 128 - extension.length)}$extension';
  }
  await platform.openSourceFile(bytes, safeName, mimeType);
}
