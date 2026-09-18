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
import 'package:gesticollecte_mobile/services/tournee_service.dart';
import 'widget_test.dart' show FixtureAdapter;

final photoBytes = base64Decode(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=');

class EvidenceAdapter extends FixtureAdapter {
  int uploads = 0;
  int analyses = 0;
  EvidenceAdapter({bool stored = false})
      : super({
          'id': 'tour-1',
          'reference': 'T-1',
          'statut': 'EN_COURS',
          'visits': [
            {
              'pointId': 'p1',
              'order': 0,
              'status': 'ARRIVED',
              if (stored) 'afterPhoto': '/uploads/photos/confirmed.png',
            }
          ],
          'pointsCollecte': [
            {'id': 'p1', 'reference': 'Point 1'}
          ],
        });

  @override
  Future<ResponseBody> fetch(RequestOptions options,
      Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    if (options.path.contains('/uploads/')) {
      return ResponseBody.fromBytes(photoBytes, 200, headers: {
        Headers.contentTypeHeader: ['image/png']
      });
    }
    if (options.method == 'POST' && options.path.endsWith('/photo')) {
      uploads++;
      data['visits'][0]['afterPhoto'] = '/uploads/photos/confirmed.png';
    }
    if (options.path.endsWith('/analyse')) {
      analyses++;
      data['visits'][0]['analysisStatus'] = 'CLEANED';
    }
    return super.fetch(options, requestStream, cancelFuture);
  }
}

Future<void> pumpUntil(WidgetTester tester, bool Function() ready) async {
  for (var turn = 0; turn < 200 && !ready(); turn++) {
    await tester
        .runAsync(() => Future<void>.delayed(const Duration(milliseconds: 10)));
    await tester.pump(const Duration(milliseconds: 10));
  }
  expect(ready(), isTrue);
}

bool canComplete(WidgetTester tester) {
  final button = find.ancestor(
      of: find.text('Confirmer la collecte et continuer'),
      matching: find.byWidgetPredicate((w) => w is ButtonStyleButton));
  return button.evaluate().isNotEmpty &&
      tester.widget<ButtonStyleButton>(button.first).onPressed != null;
}

Future<void> closePage(WidgetTester tester) async {
  await tester.pumpWidget(const SizedBox.shrink());
  // Finish authenticated image reads started by the just-disposed evidence UI.
  for (var turn = 0; turn < 3; turn++) {
    await tester
        .runAsync(() => Future<void>.delayed(const Duration(milliseconds: 10)));
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Future<void> openWithDraft(WidgetTester tester, EvidenceAdapter adapter,
    {Uint8List? draftBytes}) async {
  await tester.binding.setSurfaceSize(const Size(900, 1800));
  addTearDown(() => tester.binding.setSurfaceSize(null));
  ApiService.instance.httpClientAdapter = adapter;
  await tester.pumpWidget(MaterialApp(
      home: ActiveTrackingPage(
    tournee: const {'id': 'tour-1'},
    loadDraft: () async => PhotoDraft(
        tourId: 'tour-1',
        pointId: 'p1',
        stage: 'AFTER',
        webBytes: draftBytes ?? photoBytes),
    clearDraft: () async => throw StateError('Injected local deletion failure'),
  )));
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
      'confirmed upload still analyses and continues when cleanup fails',
      (tester) async {
    final adapter = EvidenceAdapter();
    await openWithDraft(tester, adapter);
    final retry = find.text('Réessayer l’envoi');
    await pumpUntil(tester, () => retry.evaluate().isNotEmpty);
    await tester.tap(retry);
    await pumpUntil(tester, () => adapter.analyses == 1 && canComplete(tester));
    expect(adapter.uploads, 1);
    expect(find.text('APRÈS enregistrée'), findsOneWidget);
    expect(find.text('Photo en brouillon'), findsNothing);
    expect(find.text('Confirmer la collecte et continuer'), findsOneWidget);
    expect(find.textContaining('copie locale'), findsOneWidget);
    await closePage(tester);
  });

  testWidgets(
      'matching stored evidence stops a stale draft from blocking resume',
      (tester) async {
    final adapter = EvidenceAdapter(stored: true);
    await openWithDraft(tester, adapter);
    await pumpUntil(tester,
        () => find.textContaining('copie locale').evaluate().isNotEmpty);
    expect(find.text('Photo en brouillon'), findsNothing);
    expect(find.text('Confirmer la collecte et continuer'), findsOneWidget);
    expect(canComplete(tester), isTrue);
    expect(adapter.uploads, 0);
    await closePage(tester);
  });

  test('a different replacement draft is not mistaken for the stored photo',
      () async {
    final adapter = EvidenceAdapter(stored: true);
    ApiService.instance.httpClientAdapter = adapter;
    final different = Uint8List.fromList([...photoBytes, 42]);
    expect(
        await TourneeService.draftMatchesStoredPhoto(
            PhotoDraft(
                tourId: 'tour-1',
                pointId: 'p1',
                stage: 'AFTER',
                webBytes: different),
            Map<String, dynamic>.from(adapter.data)),
        isFalse);
    expect(adapter.uploads, 0);
  });

  testWidgets('manual discard failure preserves draft and offers retry',
      (tester) async {
    final adapter = EvidenceAdapter();
    await openWithDraft(tester, adapter);
    final discard = find.text('Supprimer le brouillon');
    await pumpUntil(tester, () => discard.evaluate().isNotEmpty);
    await tester.tap(discard);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Supprimer'));
    await pumpUntil(
        tester,
        () => find
            .textContaining('Suppression locale impossible')
            .evaluate()
            .isNotEmpty);
    expect(find.text('Photo en brouillon'), findsOneWidget);
    expect(find.text('Réessayer l’envoi'), findsOneWidget);
    expect(adapter.uploads, 0);
    expect(tester.takeException(), isNull);
    await closePage(tester);
  });
}
