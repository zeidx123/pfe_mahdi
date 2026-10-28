import 'dart:async';
import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:gesticollecte_mobile/pages/active_tracking_page.dart';
import 'package:gesticollecte_mobile/services/api_service.dart';
import 'package:gesticollecte_mobile/services/auth_service.dart';
import 'package:gesticollecte_mobile/services/photo_draft_service.dart';
import 'package:gesticollecte_mobile/services/tracking_service.dart';
import 'photo_cleanup_test.dart'
    show EvidenceAdapter, photoBytes, pumpUntil, canComplete, closePage;

class QuotaAdapter extends EvidenceAdapter {
  String nextPrediction = 'CLEANED';
  Completer<void>? analysisGate;
  Completer<void>? refreshGate;
  int pendingAnalyses = 0;
  int pendingRefreshes = 0;
  QuotaAdapter({super.stored});

  @override
  Future<ResponseBody> fetch(RequestOptions options,
      Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    if (options.path.endsWith('/analyse')) {
      pendingAnalyses++;
      await analysisGate?.future;
      analyses++;
      data['visits'][0]['analysisStatus'] = nextPrediction;
      return ResponseBody.fromString(responseFixture(), 200, headers: {
        Headers.contentTypeHeader: ['application/json']
      });
    }
    if (options.path.endsWith('/photo')) {
      data['visits'][0].remove('analysisStatus');
    }
    if (options.method == 'GET' &&
        options.path == '/tournees/tour-1' &&
        refreshGate != null) {
      pendingRefreshes++;
      await refreshGate!.future;
    }
    return super.fetch(options, requestStream, cancelFuture);
  }

  String responseFixture() => const JsonEncoder().convert(data);
}

ButtonStyleButton buttonWithText(WidgetTester tester, String text) =>
    tester.widget<ButtonStyleButton>(find
        .ancestor(
            of: find.text(text),
            matching:
                find.byWidgetPredicate((widget) => widget is ButtonStyleButton))
        .first);

Future<void> openPage(WidgetTester tester, QuotaAdapter adapter,
    {bool draft = false, Uint8List? draftBytes}) async {
  await tester.binding.setSurfaceSize(const Size(1000, 2300));
  addTearDown(() => tester.binding.setSurfaceSize(null));
  ApiService.instance.httpClientAdapter = adapter;
  await tester.pumpWidget(MaterialApp(
      home: ActiveTrackingPage(
          tournee: const {'id': 'tour-1'},
          loadDraft: () async => draft
              ? PhotoDraft(
                  tourId: 'tour-1',
                  pointId: 'p1',
                  stage: 'AFTER',
                  webBytes: draftBytes ?? photoBytes)
              : null,
          clearDraft: () async {})));
  await pumpUntil(
      tester,
      () =>
          find.byType(SwitchListTile).evaluate().isNotEmpty &&
          tester
                  .widget<SwitchListTile>(find.byType(SwitchListTile))
                  .onChanged !=
              null);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await AuthService.logout();
    ApiService.sessionExpired.value = false;
    await ApiService.init();
  });
  tearDown(() async => TrackingService.stopTracking());

  testWidgets(
      'manual default uploads without AI; explicit double tap calls AI once',
      (tester) async {
    final adapter = QuotaAdapter();
    await openPage(tester, adapter, draft: true);
    expect(tester.widget<SwitchListTile>(find.byType(SwitchListTile)).value,
        isFalse);
    buttonWithText(tester, 'Réessayer l’envoi').onPressed!();
    await pumpUntil(tester, () => adapter.uploads == 1 && canComplete(tester));
    expect(adapter.analyses, 0);
    expect(find.text('Photo enregistrée. Analyse IA à lancer manuellement.'),
        findsOneWidget);
    adapter.analysisGate = Completer<void>();
    final analyse = buttonWithText(tester, 'Analyser la photo').onPressed!;
    analyse();
    analyse();
    await pumpUntil(tester, () => adapter.pendingAnalyses == 1);
    expect(adapter.analyses, 0);
    adapter.analysisGate!.complete();
    await pumpUntil(
        tester,
        () =>
            find.text('Résultat IA enregistré').evaluate().isNotEmpty &&
            canComplete(tester));
    expect(adapter.analyses, 1);
    expect(find.text('Analyser la photo'), findsNothing);
    expect(find.text('Réessayer l’analyse'), findsNothing);
    await closePage(tester);
  });

  testWidgets(
      'unavailable analysis can be manually retried without blocking completion',
      (tester) async {
    final adapter = QuotaAdapter(stored: true);
    adapter.data['visits'][0]['analysisStatus'] = 'ANALYSIS_UNAVAILABLE';
    await openPage(tester, adapter);
    expect(canComplete(tester), isTrue);
    buttonWithText(tester, 'Réessayer l’analyse').onPressed!();
    await pumpUntil(tester, () => adapter.analyses == 1 && canComplete(tester));
    expect(find.text('Résultat IA enregistré'), findsOneWidget);
    expect(find.text('Réessayer l’analyse'), findsNothing);
    await closePage(tester);
  });

  testWidgets(
      'replacement photo clears the old result without automatic analysis',
      (tester) async {
    final adapter = QuotaAdapter(stored: true);
    adapter.data['visits'][0]['analysisStatus'] = 'CLEANED';
    await openPage(tester, adapter,
        draft: true, draftBytes: Uint8List.fromList([...photoBytes, 42]));
    expect(find.text('Résultat IA enregistré'), findsOneWidget);
    buttonWithText(tester, 'Réessayer l’envoi').onPressed!();
    await pumpUntil(tester, () => adapter.uploads == 1 && canComplete(tester));
    expect(adapter.analyses, 0);
    expect(find.text('Résultat IA enregistré'), findsNothing);
    expect(find.text('Analyser la photo'), findsOneWidget);
    expect(find.text('Photo enregistrée. Analyse IA à lancer manuellement.'),
        findsOneWidget);
    await closePage(tester);
  });

  for (final status in [
    'CLEANED',
    'NOT_CLEANED',
    'OBSTRUCTED',
    'CANNOT_DETERMINE'
  ]) {
    testWidgets('saved $status is shown without offering another model call',
        (tester) async {
      final adapter = QuotaAdapter(stored: true);
      adapter.data['visits'][0]['analysisStatus'] = status;
      await openPage(tester, adapter);
      expect(find.text('Résultat IA enregistré'), findsOneWidget);
      expect(find.text('Analyser la photo'), findsNothing);
      expect(find.text('Réessayer l’analyse'), findsNothing);
      expect(adapter.analyses, 0);
      expect(canComplete(tester), isTrue);
      await closePage(tester);
    });
  }

  testWidgets(
      'slow refresh serializes stale action callbacks and duplicate refresh',
      (tester) async {
    final adapter = QuotaAdapter(stored: true);
    await openPage(tester, adapter);
    final analyse = buttonWithText(tester, 'Analyser la photo').onPressed!;
    final refresh = tester
        .widget<IconButton>(find.byWidgetPredicate(
            (widget) => widget is IconButton && widget.tooltip == 'Actualiser'))
        .onPressed!;
    adapter.refreshGate = Completer<void>();
    refresh();
    refresh();
    analyse();
    await pumpUntil(tester, () => adapter.pendingRefreshes == 1);
    expect(adapter.pendingAnalyses, 0);
    adapter.refreshGate!.complete();
    await pumpUntil(tester, () => canComplete(tester));
    expect(adapter.pendingRefreshes, 1);
    expect(adapter.analyses, 0);
    await closePage(tester);
  });

  // Local vision engine (NON TESTÉ sur le poste A : Flutter absent).
  testWidgets(
      'local probabilities and review badge are shown without another model call',
      (tester) async {
    final adapter = QuotaAdapter(stored: true);
    adapter.data['visits'][0]['analysisStatus'] = 'CLEANED';
    adapter.data['visits'][0]['analysisProbabilities'] = {
      'CANNOT_DETERMINE': 0.02,
      'DEBORDEMENT': 0.07,
      'PROPRE': 0.91
    };
    adapter.data['visits'][0]['analysisDecision'] = 'A_VERIFIER';
    await openPage(tester, adapter);
    expect(find.text('propre 91 % · débordement 7 % · indéterminé 2 %'),
        findsOneWidget);
    expect(find.text('À vérifier par le superviseur'), findsOneWidget);
    expect(find.text('Résultat IA enregistré'), findsOneWidget);
    expect(find.text('Analyser la photo'), findsNothing);
    expect(adapter.analyses, 0);
    expect(canComplete(tester), isTrue);
    await closePage(tester);
  });

  testWidgets(
      'quality refusal asks for a new photo and highlights the retake button',
      (tester) async {
    final adapter = QuotaAdapter(stored: true);
    adapter.data['visits'][0]['analysisStatus'] = 'CANNOT_DETERMINE';
    adapter.data['visits'][0]['analysisQualityIssue'] = 'TROP_SOMBRE';
    adapter.data['visits'][0]['analysisDecision'] = 'A_VERIFIER';
    await openPage(tester, adapter);
    expect(find.textContaining('Photo à refaire : trop sombre'), findsOneWidget);
    expect(
        find.ancestor(
            of: find.text('Reprendre la photo APRÈS'),
            matching: find.byWidgetPredicate((widget) => widget is FilledButton)),
        findsOneWidget);
    expect(find.text('À vérifier par le superviseur'), findsOneWidget);
    expect(adapter.analyses, 0);
    expect(canComplete(tester), isTrue);
    await closePage(tester);
  });

  testWidgets('LLM analysis without local fields shows no probabilities',
      (tester) async {
    final adapter = QuotaAdapter(stored: true);
    adapter.data['visits'][0]['analysisStatus'] = 'NOT_CLEANED';
    await openPage(tester, adapter);
    expect(find.textContaining('débordement'), findsNothing);
    expect(find.text('À vérifier par le superviseur'), findsNothing);
    expect(find.textContaining('Photo à refaire'), findsNothing);
    expect(
        find.ancestor(
            of: find.text('Reprendre la photo APRÈS'),
            matching: find.byWidgetPredicate((widget) => widget is FilledButton)),
        findsNothing);
    await closePage(tester);
  });
}
