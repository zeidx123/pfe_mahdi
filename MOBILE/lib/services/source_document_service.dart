import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'api_service.dart';

class SourceDocument {
  final Uint8List bytes;
  final String filename;
  final String mimeType;
  const SourceDocument(this.bytes, this.filename, this.mimeType);
}

class SourceDocumentService {
  static Uri sourceUri(Map<String, dynamic> source, String apiBaseUrl) {
    final id = source['documentId'];
    if (id is! String || !RegExp(r'^[a-zA-Z0-9_-]{1,128}$').hasMatch(id)) {
      throw const FormatException('Référence du document indisponible.');
    }
    final base = Uri.parse(apiBaseUrl.replaceFirst(RegExp(r'/+$'), ''));
    final expected = base.replace(path: '${base.path}/ai/documents/$id/source');
    final supplied = source['sourceUrl'];
    final uri = supplied is String && supplied.isNotEmpty
        ? base.resolve(supplied)
        : expected;
    if (uri.origin != expected.origin ||
        uri.path != expected.path ||
        uri.userInfo.isNotEmpty ||
        uri.hasQuery ||
        uri.hasFragment) {
      throw const FormatException('Adresse du document non autorisée.');
    }
    return uri;
  }

  static Future<SourceDocument> fetch(Map<String, dynamic> source,
      {Dio? client}) async {
    final api = client ?? ApiService.instance;
    final uri = sourceUri(source, api.options.baseUrl);
    // The authenticated API enforces document approval and access. Never put a
    // token in a link or follow a redirect to another download host.
    final response = await api.get<List<int>>(uri.toString(),
        options:
            Options(responseType: ResponseType.bytes, followRedirects: false));
    // XMLHttpRequest can follow redirects despite Dio's option on web.
    if (response.isRedirect == true) {
      throw const FormatException(
          'La source a été redirigée. Ouvrez le document depuis la base documentaire.');
    }
    final bytes = response.data;
    if (bytes == null || bytes.isEmpty) {
      throw const FormatException('Le document téléchargé est vide.');
    }
    var filename = '${source['documentName'] ?? 'document'}'
        .replaceAll('\\', '/')
        .split('/')
        .last
        .replaceAll(RegExp(r'[\x00-\x1f\x7f]'), '')
        .trim();
    if (filename.isEmpty || filename == '.' || filename == '..') {
      filename = 'document';
    }
    final extension = filename.split('.').last.toLowerCase();
    final mime = switch (extension) {
      'pdf' => 'application/pdf',
      'docx' =>
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      'md' || 'txt' => 'text/plain',
      _ => 'application/octet-stream',
    };
    return SourceDocument(Uint8List.fromList(bytes), filename, mime);
  }
}
