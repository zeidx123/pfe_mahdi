import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:gesticollecte_mobile/core/constants.dart';
import 'package:gesticollecte_mobile/main.dart';
import 'package:gesticollecte_mobile/models/tour_progress.dart';
import 'package:gesticollecte_mobile/pages/active_tracking_page.dart';
import 'package:gesticollecte_mobile/services/api_service.dart';
import 'package:gesticollecte_mobile/services/auth_service.dart';
import 'package:gesticollecte_mobile/services/tournee_service.dart';
import 'package:gesticollecte_mobile/services/photo_draft_service.dart';
import 'package:gesticollecte_mobile/services/tracking_service.dart';

class FixtureAdapter implements HttpClientAdapter {
  dynamic data;
  int statusCode;
  RequestOptions? lastRequest;
  FixtureAdapter(this.data, [this.statusCode = 200]);
  @override
  Future<ResponseBody> fetch(RequestOptions options,
      Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    lastRequest = options;
    return ResponseBody.fromString(jsonEncode(data), statusCode, headers: {
      Headers.contentTypeHeader: ['application/json']
    });
  }

  @override
  void close({bool force = false}) {}
}

String testToken() => 'header.${base64Url.encode(utf8.encode(jsonEncode({
          'exp': 4102444800
        }))).replaceAll('=', '')}.signature';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await AuthService.logout();
    ApiService.sessionExpired.value = false;
    await ApiService.init();
  });
  tearDown(() async {
    await TrackingService.stopTracking();
  });

  test('flat Spring login is saved and can be restored without nested user',
      () async {
    final adapter = FixtureAdapter({
      'token': testToken(),
      'id': 'driver-1',
      'email': 'driver@example.test',
      'prenom': 'Amine',
      'nom': 'Test',
      'role': 'CHAUFFEUR'
    });
    ApiService.instance.httpClientAdapter = adapter;
    expect(
        await AuthService.login('driver@example.test', ' password '), isTrue);
    expect(AuthService.currentUser?['id'], 'driver-1');
    expect(adapter.lastRequest?.data['password'], ' password ');
    expect(await AuthService.isLoggedIn(), isTrue);
    expect(AuthService.currentUser?['prenom'], 'Amine');
    final prefs = await SharedPreferences.getInstance();
    expect(jsonDecode(prefs.getString(AppConstants.userKey)!)['token'], isNull);
  });

  test(
      'supervisor cannot enter the driver session and corrupt sessions are removed',
      () async {
    ApiService.instance.httpClientAdapter = FixtureAdapter(
        {'token': testToken(), 'id': 'supervisor', 'role': 'SUPERVISEUR'});
    expect(await AuthService.login('supervisor@example.test', 'password'),
        isFalse);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(AppConstants.tokenKey, 'bad');
    await prefs.setString(AppConstants.userKey, 'null');
    expect(await AuthService.isLoggedIn(), isFalse);
    expect(prefs.containsKey(AppConstants.tokenKey), isFalse);
  });

  test('forbidden actions preserve login; unauthorized responses expire it',
      () async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(AppConstants.tokenKey, testToken());
    final adapter = FixtureAdapter({'message': 'Forbidden'}, 403);
    ApiService.instance.httpClientAdapter = adapter;
    await expectLater(ApiService.instance.get('/tournees/other'),
        throwsA(isA<DioException>()));
    expect(prefs.containsKey(AppConstants.tokenKey), isTrue);
    expect(ApiService.sessionExpired.value, isFalse);
    adapter.statusCode = 401;
    await expectLater(ApiService.instance.get('/tournees/other'),
        throwsA(isA<DioException>()));
    expect(prefs.containsKey(AppConstants.tokenKey), isFalse);
    expect(ApiService.sessionExpired.value, isTrue);
  });

  test('file URLs cannot forward tokens to another origin', () {
    expect(ApiService.protectedFileUrl('after.jpg'),
        endsWith('/uploads/photos/after.jpg'));
    expect(ApiService.protectedFileUrl('/uploads/photos/after.jpg'),
        endsWith('/uploads/photos/after.jpg'));
    expect(
        () => ApiService.protectedFileUrl(
            'https://other.example/uploads/photos/after.jpg'),
        throwsFormatException);
    expect(() => ApiService.protectedFileUrl('/auth/login'),
        throwsFormatException);
  });

  test('execution actions use the validated visit endpoints', () async {
    final adapter = FixtureAdapter({'id': 'tour-1', 'statut': 'EN_COURS'});
    ApiService.instance.httpClientAdapter = adapter;
    await TourneeService.demarrerTournee('tour-1');
    expect(adapter.lastRequest?.path, '/tournees/tour-1/start');
    await TourneeService.complete('tour-1', 'point-2',
        outcome: 'ISSUE',
        notes: 'Accès obstrué',
        driverAssessment: 'OBSTRUCTED');
    expect(
        adapter.lastRequest?.path, '/tournees/tour-1/points/point-2/complete');
    expect(adapter.lastRequest?.data, {
      'outcome': 'ISSUE',
      'notes': 'Accès obstrué',
      'driverAssessment': 'OBSTRUCTED'
    });
    await TourneeService.terminerTournee('tour-1');
    expect(adapter.lastRequest?.data, {'returnedToDepot': true});
  });

  test(
      'photo retry uploads the existing evidence with explicit image MIME and stage',
      () async {
    final adapter = FixtureAdapter({'id': 'tour-1', 'visits': []});
    ApiService.instance.httpClientAdapter = adapter;
    await TourneeService.upload(PhotoDraft(
        tourId: 'tour-1',
        pointId: 'p1',
        stage: 'AFTER',
        webBytes: Uint8List.fromList([0xff, 0xd8, 0xff, 0xe0])));
    expect(adapter.lastRequest?.path, '/tournees/tour-1/points/p1/photo');
    final form = adapter.lastRequest!.data as FormData;
    expect(form.fields.single.value, 'AFTER');
    expect(form.files.single.value.contentType.toString(), 'image/jpeg');
    expect(form.files.single.value.filename, 'after.jpg');
  });

  test('PNG evidence keeps its filename and MIME through manual retry',
      () async {
    final adapter = FixtureAdapter({'id': 'tour-1', 'visits': []});
    ApiService.instance.httpClientAdapter = adapter;
    final draft = PhotoDraft(
        tourId: 'tour-1',
        pointId: 'p1',
        stage: 'BEFORE',
        webBytes: Uint8List.fromList(
            [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]));
    for (var attempt = 0; attempt < 2; attempt++) {
      await TourneeService.upload(draft);
      final form = adapter.lastRequest!.data as FormData;
      expect(form.fields.single.value, 'BEFORE');
      expect(form.files.single.value.filename, 'before.png');
      expect(form.files.single.value.contentType.toString(), 'image/png');
    }
  });

  test('unsupported or oversized photo is rejected before upload', () async {
    final adapter = FixtureAdapter({'id': 'tour-1'});
    ApiService.instance.httpClientAdapter = adapter;
    for (final bytes in [
      Uint8List.fromList(utf8.encode('GIF89a')),
      Uint8List.fromList([0x89]),
      Uint8List(10 * 1024 * 1024 + 1),
    ]) {
      await expectLater(
          TourneeService.upload(PhotoDraft(
              tourId: 'tour-1',
              pointId: 'p1',
              stage: 'AFTER',
              webBytes: bytes)),
          throwsFormatException);
      expect(adapter.lastRequest, isNull);
    }
  });

  test('resume uses ordered persisted visits and then returns to depot', () {
    final tour = <String, dynamic>{
      'visits': [
        {
          'pointId': 'p2',
          'order': 1,
          'status': 'PENDING',
          'plannedServiceSeconds': 300
        },
        {
          'pointId': 'p1',
          'order': 0,
          'status': 'ISSUE_REPORTED',
          'plannedServiceSeconds': 300
        },
      ],
      'pointsCollecte': [
        {'id': 'p2', 'latitude': 36.8, 'longitude': 10.2}
      ],
      'route': {
        'depot': {'latitude': 36.7, 'longitude': 10.1},
        'geometry': [
          [10.1, 36.7],
          [10.2, 36.8]
        ],
        'legs': [
          {'durationSeconds': 60},
          {'durationSeconds': 120},
          {'durationSeconds': 180}
        ]
      },
    };
    final progress = TourProgress(tour);
    expect(progress.currentVisit?['pointId'], 'p2');
    expect(progress.destination?['id'], 'p2');
    expect(progress.remainingTravelSeconds, 300);
    expect(progress.remainingServiceSeconds, 300);
    expect(progress.geometry.first.latitude, 36.7);
    expect(progress.geometry.first.longitude, 10.1);
    (tour['visits'] as List).first['status'] = 'ARRIVED';
    expect(progress.remainingTravelSeconds, 180);
    (tour['visits'] as List).first['status'] = 'COMPLETED_REPORTED';
    expect(progress.allHandled, isTrue);
    expect(progress.destination?['latitude'], 36.7);
    expect(
        TourProgress.navigationUri(
                TourProgress.coordinate(progress.destination)!)
            .queryParameters['destination'],
        '36.7,10.1');
    expect(TourProgress.assessmentLabel('ANALYSIS_UNAVAILABLE'),
        'Analyse indisponible');
    expect(TourProgress.assessmentLabel('NORMAL'), 'Aucune analyse disponible');
  });

  testWidgets(
      'arrived visit without AFTER evidence cannot show normal completion',
      (tester) async {
    ApiService.instance.httpClientAdapter = FixtureAdapter({
      'id': 'tour-1',
      'reference': 'T-1',
      'statut': 'EN_COURS',
      'visits': [
        {'pointId': 'p1', 'order': 0, 'status': 'ARRIVED'}
      ],
      'pointsCollecte': [
        {'id': 'p1', 'reference': 'Point 1'}
      ],
    });
    final capture =
        find.text(kIsWeb ? 'Choisir la photo APRÈS' : 'Prendre la photo APRÈS');
    await tester.pumpWidget(
        const MaterialApp(home: ActiveTrackingPage(tournee: {'id': 'tour-1'})));
    // Alternate browser event turns with the widget fake clock: IndexedDB
    // callbacks cannot complete while pumpAndSettle only advances fake time.
    for (var turn = 0; turn < 100 && capture.evaluate().isEmpty; turn++) {
      await tester.runAsync(
          () => Future<void>.delayed(const Duration(milliseconds: 10)));
      await tester.pump(const Duration(milliseconds: 10));
    }
    expect(capture, findsOneWidget,
        reason: find
            .byType(Text)
            .evaluate()
            .map((e) => (e.widget as Text).data)
            .join(' | '));
    expect(find.text('Confirmer la collecte et continuer'), findsNothing);
    expect(find.text('Signaler un problème et continuer'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
      'pending inaccessible point can report an issue without arrival or AFTER',
      (tester) async {
    final adapter = FixtureAdapter({
      'id': 'tour-1',
      'reference': 'T-1',
      'statut': 'EN_COURS',
      'visits': [
        {'pointId': 'p1', 'order': 0, 'status': 'PENDING'}
      ],
      'pointsCollecte': [
        {'id': 'p1', 'reference': 'Point inaccessible'}
      ],
    });
    ApiService.instance.httpClientAdapter = adapter;
    final report = find.text('Signaler un problème et continuer');
    await tester.pumpWidget(
        const MaterialApp(home: ActiveTrackingPage(tournee: {'id': 'tour-1'})));
    for (var turn = 0; turn < 100 && report.evaluate().isEmpty; turn++) {
      await tester.runAsync(
          () => Future<void>.delayed(const Duration(milliseconds: 10)));
      await tester.pump(const Duration(milliseconds: 10));
    }
    expect(report, findsOneWidget);
    expect(find.text('Je suis arrivé'), findsOneWidget);
    expect(find.text('Confirmer la collecte et continuer'), findsNothing);
    await tester.ensureVisible(report);
    await tester.pumpAndSettle();
    await tester.tap(report);
    await tester.pumpAndSettle();
    expect(find.text('Motif obligatoire'), findsOneWidget);
    await tester.tap(find.text('Enregistrer'));
    await tester.pumpAndSettle();
    expect(find.text('Décrivez le problème'), findsOneWidget);
    expect(adapter.lastRequest?.method, 'GET');
    await tester.tap(find.text('Annuler'));
    await tester.pumpAndSettle();
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
      'logged-out app opens the driver login instead of the obsolete counter',
      (tester) async {
    SharedPreferences.setMockInitialValues({'onboarding_completed': true});
    await tester.pumpWidget(const GestiCollecteApp());
    await tester.pump(const Duration(seconds: 2));
    await tester.pumpAndSettle();
    expect(find.text('Espace Chauffeur'), findsOneWidget);
    expect(find.text('Se connecter'), findsOneWidget);
    expect(find.text('Configurer le serveur'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('tour URL reload restores session then opens assignments',
      (tester) async {
    tester.binding.platformDispatcher.defaultRouteNameTestValue =
        '/tournee-detail';
    addTearDown(
        tester.binding.platformDispatcher.clearDefaultRouteNameTestValue);
    SharedPreferences.setMockInitialValues({
      'onboarding_completed': true,
      AppConstants.tokenKey: testToken(),
      AppConstants.userKey: jsonEncode(
          {'id': 'driver-1', 'role': 'CHAUFFEUR', 'prenom': 'Amine'}),
    });
    ApiService.instance.httpClientAdapter = FixtureAdapter([]);
    await tester.pumpWidget(const GestiCollecteApp());
    await tester.pump(const Duration(seconds: 2));
    final loaded = find.text('Aucune tournée planifiée');
    for (var turn = 0; turn < 100 && loaded.evaluate().isEmpty; turn++) {
      await tester.runAsync(
          () => Future<void>.delayed(const Duration(milliseconds: 10)));
      await tester.pump(const Duration(milliseconds: 10));
    }
    expect(AuthService.currentUser?['id'], 'driver-1');
    expect(find.text('Mes tournées de collecte'), findsOneWidget);
    expect(loaded, findsOneWidget);
    expect(find.text('Tournée introuvable. Revenez à vos affectations.'),
        findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
