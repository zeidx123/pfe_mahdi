import 'dart:async';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:geolocator/geolocator.dart';
import 'api_service.dart';
import '../core/constants.dart';

class TrackingState {
  final bool active;
  final Position? position;
  final DateTime? sentAt;
  final String? error;
  const TrackingState(
      {this.active = false, this.position, this.sentAt, this.error});

  bool get stale =>
      sentAt == null || DateTime.now().difference(sentAt!).inSeconds > 40;
}

class TrackingService {
  static final ValueNotifier<TrackingState> state =
      ValueNotifier(const TrackingState());
  static StreamSubscription<Position>? _subscription;
  static String? _tourneeId;
  static bool _sending = false;
  static int _generation = 0;
  static bool get isTracking => _subscription != null;

  static Future<bool> requestPermission() async {
    if (!await Geolocator.isLocationServiceEnabled()) return false;
    var permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
    }
    return permission == LocationPermission.whileInUse ||
        permission == LocationPermission.always;
  }

  static Future<void> startTracking(
      {required String camionId,
      required String camionMatricule,
      required String tourneeId,
      required String tourneeReference}) async {
    if (_tourneeId == tourneeId && isTracking) return;
    await stopTracking();
    if (camionId.isEmpty || tourneeId.isEmpty) {
      state.value = const TrackingState(
          error: 'Camion ou tournée manquant : suivi indisponible.');
      return;
    }
    final generation = _generation;
    final allowed = await requestPermission();
    if (generation != _generation) return;
    if (!allowed) {
      state.value = const TrackingState(
          error:
              'Activez le GPS et autorisez la localisation pour partager votre position.');
      return;
    }
    _tourneeId = tourneeId;
    state.value = const TrackingState(active: true);
    final LocationSettings settings = !kIsWeb &&
            defaultTargetPlatform == TargetPlatform.android
        ? AndroidSettings(
            accuracy: LocationAccuracy.high,
            distanceFilter: 0,
            intervalDuration:
                const Duration(seconds: AppConstants.gpsIntervalSeconds),
            foregroundNotificationConfig: const ForegroundNotificationConfig(
              notificationTitle: 'Tournée GestiCollecte en cours',
              notificationText:
                  'Votre position est partagée avec le superviseur.',
              enableWakeLock: true,
            ),
          )
        : const LocationSettings(
            accuracy: LocationAccuracy.high, distanceFilter: 0);
    _subscription = Geolocator.getPositionStream(locationSettings: settings)
        .listen((position) async {
      if (generation != _generation) return;
      state.value = TrackingState(
          active: true,
          position: position,
          sentAt: state.value.sentAt,
          error: state.value.error);
      if (_sending) return;
      _sending = true;
      try {
        await ApiService.instance.post('/tracking/position', data: {
          'camionId': camionId,
          'camionMatricule': camionMatricule,
          'tourneeId': tourneeId,
          'tourneeReference': tourneeReference,
          'latitude': position.latitude,
          'longitude': position.longitude,
          'vitesse': position.speed.isFinite && position.speed >= 0
              ? position.speed * 3.6
              : null,
          'statut': 'EN_ROUTE',
        });
        if (generation == _generation) {
          state.value = TrackingState(
              active: true, position: position, sentAt: DateTime.now());
        }
      } catch (error) {
        if (generation == _generation &&
            error is DioException &&
            [403, 409].contains(error.response?.statusCode)) {
          await stopTracking();
          state.value = TrackingState(error: ApiService.errorMessage(error));
          return;
        }
        if (generation == _generation) {
          state.value = TrackingState(
              active: true,
              position: position,
              sentAt: state.value.sentAt,
              error: ApiService.errorMessage(error));
        }
      } finally {
        if (generation == _generation) _sending = false;
      }
    }, onError: (Object error) {
      if (generation != _generation) return;
      state.value = TrackingState(
          position: state.value.position,
          sentAt: state.value.sentAt,
          error:
              'Suivi GPS interrompu. Réactivez le GPS puis appuyez sur Réessayer.');
      _subscription?.cancel();
      _subscription = null;
    });
  }

  static Future<void> stopTracking() async {
    _generation++;
    await _subscription?.cancel();
    _subscription = null;
    _tourneeId = null;
    _sending = false;
    state.value = const TrackingState();
  }
}
