import 'dart:convert';
import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gesticollecte_mobile/core/app_theme.dart';
import 'package:gesticollecte_mobile/pages/assistant_page.dart';
import 'package:gesticollecte_mobile/services/api_service.dart';
import 'package:gesticollecte_mobile/services/source_document_service.dart';
import 'package:gesticollecte_mobile/widgets/rag_source_card.dart';

const source = {
  'documentId': 'doc-1',
  'documentName': 'Guide.md',
  'sourceUrl': '/api/ai/documents/doc-1/source',
  'snippet': 'La photo APRÈS est obligatoire.',
  'sectionTitle': 'Photos',
};

class AssistantAdapter implements HttpClientAdapter {
  final List<Map<String, dynamic>> questions = [];
  List<Map<String, dynamic>> responseSources = [source];
  RequestOptions? download;
  int downloadStatus = 200;
  bool redirected = false;
  @override
  void close({bool force = false}) {}
  @override
  Future<ResponseBody> fetch(RequestOptions options,
      Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    if (options.path.endsWith('/source')) {
      download = options;
      return ResponseBody.fromBytes(
          utf8.encode('Source originale'), downloadStatus,
          isRedirect: redirected,
          headers: {
            Headers.contentTypeHeader: ['text/plain']
          });
    }
    Object data = [];
    if (options.method == 'POST') {
      questions.add(Map<String, dynamic>.from(options.data));
      data = {
        'conversationId': 'conv-1',
        'status': questions.length == 1 ? 'SOURCE_EXCERPTS' : 'ANSWERED',
        'answer': questions.length == 1
            ? 'DO_NOT_DUPLICATE_SOURCE_DUMP'
            : 'Une photo **APRÈS** est requise.',
        'sources': responseSources,
      };
    }
    return ResponseBody.fromString(jsonEncode(data), 200, headers: {
      Headers.contentTypeHeader: ['application/json']
    });
  }
}

void main() {
  test('source links must identify the same document on the API origin', () {
    const base = 'http://localhost:8080/api/';
    expect(SourceDocumentService.sourceUri(source, base).toString(),
        'http://localhost:8080/api/ai/documents/doc-1/source');
    for (final url in [
      'https://untrusted.example/api/ai/documents/doc-1/source',
      '/api/ai/documents/doc-2/source',
      '/api/ai/documents/doc-1/source?token=anything',
      '/api/ai/documents/doc-1/source#fragment',
      'http://user@localhost:8080/api/ai/documents/doc-1/source',
    ]) {
      expect(
          () => SourceDocumentService.sourceUri(
              {...source, 'sourceUrl': url}, base),
          throwsFormatException);
    }
  });

  test('source download preserves bytes and refuses redirects', () async {
    final adapter = AssistantAdapter();
    final client = Dio(BaseOptions(baseUrl: 'http://localhost:8080/api'));
    client.httpClientAdapter = adapter;
    final file = await SourceDocumentService.fetch(source, client: client);
    expect(utf8.decode(file.bytes), 'Source originale');
    expect(file.filename, 'Guide.md');
    expect(adapter.download!.followRedirects, isFalse);
    adapter.downloadStatus = 302;
    await expectLater(SourceDocumentService.fetch(source, client: client),
        throwsA(isA<DioException>()));
    adapter.downloadStatus = 200;
    adapter.redirected = true;
    await expectLater(SourceDocumentService.fetch(source, client: client),
        throwsFormatException);
  });

  testWidgets('excerpts stay distinct and manual retry retains conversation',
      (tester) async {
    final adapter = AssistantAdapter();
    ApiService.instance.options.baseUrl = 'http://localhost:8080/api';
    ApiService.instance.httpClientAdapter = adapter;
    await tester.pumpWidget(
        MaterialApp(theme: GestiTheme.light, home: const AssistantPage()));
    await tester.pumpAndSettle();
    await tester.enterText(
        find.byType(TextField).first, 'Quelle photo faut-il fournir ?');
    await tester.pump();
    await tester.tap(find.byTooltip('Envoyer la question'));
    await tester.pumpAndSettle();
    expect(find.text('Extraits disponibles · réponse IA indisponible'),
        findsOneWidget);
    expect(find.text('DO_NOT_DUPLICATE_SOURCE_DUMP'), findsNothing);
    expect(find.byType(RagSourceCard), findsOneWidget);
    await tester.ensureVisible(find.text('Réessayer la réponse'));
    await tester.tap(find.text('Réessayer la réponse'));
    await tester.pumpAndSettle();
    expect(adapter.questions.length, 2);
    expect(adapter.questions.last['conversationId'], 'conv-1');
    expect(
        adapter.questions.last['question'], 'Quelle photo faut-il fournir ?');
    expect(find.text('Réponse documentée'), findsOneWidget);
    expect(find.text('Réessayer la réponse'), findsNothing);
    await tester.ensureVisible(find.text('Préciser ma question').last);
    await tester.tap(find.text('Préciser ma question').last);
    await tester.pumpAndSettle();
    final field = tester.widget<TextField>(find.byType(TextField).first);
    expect(field.controller!.text, 'Quelle photo faut-il fournir ?');
    expect(tester.takeException(), isNull);
  });

  testWidgets('long excerpts reveal the reply introduction on a small screen',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(390, 844));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final adapter = AssistantAdapter()
      ..responseSources = List.generate(
          4,
          (index) => {
                ...source,
                'chunkId': 'chunk-$index',
                'snippet': List.generate(
                        24,
                        (paragraph) =>
                            'Passage ${paragraph + 1}. La photo APRÈS permet de consulter l’état visible du point. La collecte reste une déclaration du chauffeur et peut être revue par le superviseur.')
                    .join('\n\n'),
              });
    ApiService.instance.options.baseUrl = 'http://localhost:8080/api';
    ApiService.instance.httpClientAdapter = adapter;
    await tester.pumpWidget(
        MaterialApp(theme: GestiTheme.light, home: const AssistantPage()));
    await tester.pumpAndSettle();
    await tester.enterText(
        find.byType(TextField).first, 'Quelle photo faut-il fournir ?');
    await tester.pump();
    await tester.tap(find.byTooltip('Envoyer la question'));
    await tester.pumpAndSettle();

    final viewport = tester.getRect(find.byType(SingleChildScrollView).first);
    final heading = tester.getRect(find.text('GestiCollecte'));
    final introduction = tester.getRect(find.text(
        'La réponse IA n’a pas pu être générée. Vous pouvez lire les passages retrouvés et ouvrir les documents ci-dessous.'));
    expect(find.byType(RagSourceCard), findsNWidgets(4));
    expect(heading.top, greaterThanOrEqualTo(viewport.top),
        reason: 'The beginning of a tall reply must remain below the app bar.');
    expect(heading.bottom, lessThan(viewport.top + 100));
    expect(introduction.bottom, lessThanOrEqualTo(viewport.bottom),
        reason: 'The explanation of excerpt-only results should be visible.');
    expect(tester.takeException(), isNull);
  });

  testWidgets('history search exposes its own label and input-sized semantics',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(390, 844));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final semantics = tester.ensureSemantics();
    try {
      ApiService.instance.options.baseUrl = 'http://localhost:8080/api';
      ApiService.instance.httpClientAdapter = AssistantAdapter();
      await tester.pumpWidget(
          MaterialApp(theme: GestiTheme.light, home: const AssistantPage()));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Historique des discussions'));
      await tester.pumpAndSettle();

      final search = find.byWidgetPredicate((widget) =>
          widget is TextField &&
          widget.decoration?.labelText == 'Rechercher une discussion');
      final input =
          find.descendant(of: search, matching: find.byType(EditableText));
      final node = tester.getSemantics(input);
      expect(node.label, 'Rechercher une discussion');
      expect(node.rect.height, lessThan(120),
          reason: 'The editable input must not inherit the drawer bounds.');
      await tester.enterText(search, 'photos');
      await tester.pumpAndSettle();
      expect(tester.getSemantics(input).value, 'photos');
      expect(tester.takeException(), isNull);
    } finally {
      semantics.dispose();
    }
  });

  testWidgets('source access failure is visible and leaves excerpt readable',
      (tester) async {
    final adapter = AssistantAdapter()..downloadStatus = 403;
    ApiService.instance.options.baseUrl = 'http://localhost:8080/api';
    ApiService.instance.httpClientAdapter = adapter;
    await tester.pumpWidget(MaterialApp(
        theme: GestiTheme.light,
        home: const Scaffold(
            body: RagSourceCard(source: source, initiallyExpanded: true))));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Ouvrir le document'));
    await tester.pumpAndSettle();
    expect(find.text('Vous ne pouvez pas accéder à cette ressource.'),
        findsOneWidget);
    expect(find.text('Extrait cité'), findsOneWidget);
    expect(find.text('Ouvrir le document'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
