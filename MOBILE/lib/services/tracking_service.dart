import 'dart:async';
import 'package:geolocator/geolocator.dart';
import 'api_service.dart';
import '../core/constants.dart';

class TrackingService {
  static Timer? _timer;
  static bool _isTracking = false;

  static bool get isTracking => _isTracking;

  static Future<bool> requestPermission() async {
    LocationPermission permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
    }
    return permission == LocationPermission.whileInUse ||
        permission == LocationPermission.always;
  }

  static Future<void> startTracking({
    required String camionId,
    required String camionMatricule,
    required String tourneeId,
    required String tourneeReference,
  }) async {
    if (_isTracking) return;

    final hasPermission = await requestPermission();
    if (!hasPermission) return;

    _isTracking = true;

    // Envoyer immédiatement une première position
    await _sendPosition(
      camionId: camionId,
      camionMatricule: camionMatricule,
      tourneeId: tourneeId,
      tourneeReference: tourneeReference,
    );

    // Puis toutes les X secondes
    _timer = Timer.periodic(
      Duration(seconds: AppConstants.gpsIntervalSeconds),
      (_) => _sendPosition(
        camionId: camionId,
        camionMatricule: camionMatricule,
        tourneeId: tourneeId,
        tourneeReference: tourneeReference,
      ),
    );
  }

  static void stopTracking() {
    _timer?.cancel();
    _isTracking = false;
  }

  static Future<void> _sendPosition({
    required String camionId,
    required String camionMatricule,
    required String tourneeId,
    required String tourneeReference,
  }) async {
    try {
      final position = await Geolocator.getCurrentPosition(
        desiredAccuracy: LocationAccuracy.high,
      );

      await ApiService.instance.post('/tracking/position', data: {
        'camionId': camionId,
        'camionMatricule': camionMatricule,
        'tourneeId': tourneeId,
        'tourneeReference': tourneeReference,
        'latitude': position.latitude,
        'longitude': position.longitude,
        'vitesse': position.speed * 3.6, // m/s -> km/h
        'niveauCarburant': 75.0, // Valeur simulée (à relier à l'OBD)
        'statut': 'EN_ROUTE',
      });
    } catch (e) {
      // Silencieux — pas de GPS disponible ou erreur réseau
    }
  }
}
