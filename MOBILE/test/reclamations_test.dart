import 'dart:async';
import 'dart:collection';
import 'dart:convert';
import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gesticollecte_mobile/core/app_theme.dart';
import 'package:gesticollecte_mobile/pages/reclamations_page.dart';
import 'package:gesticollecte_mobile/services/api_service.dart';

const _claim = {
  'id': 'claim-1',
  'titre': 'Panne du camion au départ',
  'type': 'PANNE_MECANIQUE',
  'description': 'Le moteur ne démarre pas. Le camion est stationné au dépôt.',
  'statut': 'EN_ATTENTE',
  'dateCreation': '2026-09-23T09:15:00',
};

ResponseBody _jsonResponse(Object data, {int status = 200}) =>
    ResponseBody.fromString(jsonEncode(data), status, headers: {
      Headers.contentTypeHeader: ['application/json'],
    });

class ReclamationsAdapter implements HttpClientAdapter {
  final getResponses = Queue<Future<ResponseBody>>();
  final postResponses = Queue<Future<ResponseBody>>();
  final submissions = <Map<String, dynamic>>[];
  int getCount = 0;

  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(RequestOptions options,
      Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    if (options.method == 'GET' &&
        options.path == '/reclamations/mes-reclamations') {
      getCount++;
      return getResponses.removeFirst();
    }
    if (options.method == 'POST' && options.path == '/reclamations') {
      submissions.add(Map<String, dynamic>.from(options.data));
      return postResponses.removeFirst();
    }
    throw StateError('Unexpected request: ${options.method} ${options.path}');
  }
}

Finder _field(String label) => find.byWidgetPredicate(
    (widget) => widget is TextField && widget.decoration?.labelText == label);

Future<void> _mount(WidgetTester tester, ReclamationsAdapter adapter) async {
  ApiService.instance.options.baseUrl = 'http://localhost:8080/api';
  ApiService.instance.httpClientAdapter = adapter;
  await tester.pumpWidget(
      MaterialApp(theme: GestiTheme.light, home: const ReclamationsPage()));
}

Future<void> _waitForRequest(
    WidgetTester tester, bool Function() requestStarted) async {
  // Dio can schedule its adapter after an event-loop turn. Keep the response
  // pending without pumpAndSettle, which waits on the loading animation.
  for (var attempt = 0; attempt < 100 && !requestStarted(); attempt++) {
    await tester
        .runAsync(() => Future<void>.delayed(const Duration(milliseconds: 1)));
    await tester.pump(const Duration(milliseconds: 10));
  }
}

void main() {
  testWidgets(
      'failed claim loading stays distinct from an empty list and retries',
      (tester) async {
    const error = 'Service réclamations momentanément indisponible.';
    final adapter = ReclamationsAdapter()
      ..getResponses.addAll([
        Future.value(_jsonResponse({'message': error}, status: 503)),
        Future.value(_jsonResponse([_claim])),
      ]);
    await _mount(tester, adapter);
    await tester.pumpAndSettle();

    expect(adapter.getCount, 1);
    expect(find.text(error), findsOneWidget);
    expect(find.textContaining('Aucune réclamation'), findsNothing);
    await tester.ensureVisible(find.text('Réessayer'));
    await tester.tap(find.text('Réessayer'));
    await tester.pumpAndSettle();

    expect(adapter.getCount, 2);
    expect(find.text(_claim['titre']!), findsOneWidget);
    expect(find.text(error), findsNothing);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
      'claim validation and pending state prevent duplicates while failure retains the draft',
      (tester) async {
    const error = 'Envoi indisponible. Votre réclamation peut être réessayée.';
    final firstPost = Completer<ResponseBody>();
    final adapter = ReclamationsAdapter()
      ..getResponses.addAll([
        Future.value(_jsonResponse([])),
        Future.value(_jsonResponse([_claim])),
      ])
      ..postResponses.addAll([
        firstPost.future,
        Future.value(_jsonResponse(_claim)),
      ]);
    await _mount(tester, adapter);
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Nouvelle réclamation').first);
    await tester.tap(find.text('Nouvelle réclamation').first);
    await tester.pumpAndSettle();

    expect(find.text('Type de problème'), findsOneWidget);
    await tester.ensureVisible(find.text('Envoyer la réclamation'));
    await tester.tap(find.text('Envoyer la réclamation'));
    await tester.pumpAndSettle();
    expect(adapter.submissions, isEmpty);
    expect(tester.widget<TextField>(_field('Sujet')).decoration?.errorText,
        isNotEmpty);
    expect(
        tester.widget<TextField>(_field('Description')).decoration?.errorText,
        isNotEmpty);

    await tester.enterText(_field('Sujet'), _claim['titre']!);
    await tester.enterText(_field('Description'), _claim['description']!);
    await tester.ensureVisible(find.text('Envoyer la réclamation'));
    await tester.tap(find.text('Envoyer la réclamation'));
    await _waitForRequest(tester, () => adapter.submissions.isNotEmpty);

    expect(adapter.submissions.length, 1);
    final pendingButton = find.ancestor(
      of: find.text('Envoi en cours…'),
      matching: find.byWidgetPredicate((widget) => widget is ButtonStyleButton),
    );
    expect(tester.widget<ButtonStyleButton>(pendingButton).onPressed, isNull);
    await tester.tap(find.text('Envoi en cours…'));
    await tester.pump();
    expect(adapter.submissions.length, 1,
        reason: 'A second tap must not create another pending request.');

    final sheet = tester.getRect(find.byType(BottomSheet));
    await tester.dragFrom(
        Offset(sheet.center.dx, sheet.top + 14), const Offset(0, 600));
    await tester.pump(const Duration(milliseconds: 400));
    expect(_field('Sujet'), findsOneWidget,
        reason:
            'Swiping must not discard a draft while its request is pending.');
    expect(adapter.submissions.length, 1);

    firstPost.complete(_jsonResponse({'message': error}, status: 503));
    await _waitForRequest(
        tester, () => find.textContaining(error).evaluate().isNotEmpty);
    await tester.pumpAndSettle();
    expect(find.textContaining(error), findsOneWidget);
    expect(tester.widget<TextField>(_field('Sujet')).controller!.text,
        _claim['titre']);
    expect(tester.widget<TextField>(_field('Description')).controller!.text,
        _claim['description']);
    expect(find.text('Envoyer la réclamation'), findsOneWidget);
    expect(adapter.submissions.length, 1,
        reason: 'Retry requires an explicit driver action.');

    await tester.ensureVisible(find.text('Envoyer la réclamation'));
    await tester.tap(find.text('Envoyer la réclamation'));
    await tester.pumpAndSettle();
    expect(adapter.submissions.length, 2);
    expect(adapter.submissions.last, adapter.submissions.first);
    expect(adapter.submissions.last['titre'], _claim['titre']);
    expect(adapter.submissions.last['description'], _claim['description']);
    expect(adapter.submissions.last['type'], _claim['type']);
    expect(adapter.getCount, 2);
    expect(_field('Sujet'), findsNothing);
    expect(_field('Description'), findsNothing);
    expect(find.text(_claim['titre']!), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
      'late successful or failed claim loads do not update disposed state',
      (tester) async {
    for (final status in [200, 503]) {
      final pendingGet = Completer<ResponseBody>();
      final adapter = ReclamationsAdapter()
        ..getResponses.add(pendingGet.future);
      await _mount(tester, adapter);
      await _waitForRequest(tester, () => adapter.getCount > 0);
      expect(adapter.getCount, 1);
      await tester.pumpWidget(const SizedBox.shrink());

      pendingGet.complete(_jsonResponse(
        status == 200 ? [_claim] : {'message': 'Service indisponible'},
        status: status,
      ));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull,
          reason: 'A $status response after navigation must be ignored.');
    }
  });
}
