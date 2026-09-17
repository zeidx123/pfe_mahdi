import 'package:flutter/foundation.dart';

// lib/core/constants.dart
class AppConstants {
  // Backend URL
  // Sur Chrome (Web): localhost
  // Sur appareil physique: IP LAN
  static const String baseUrl = kIsWeb ? 'http://localhost:8080/api' : 'http://192.168.1.82:8080/api';
  static const String wsUrl = kIsWeb ? 'http://localhost:8080/ws' : 'http://192.168.1.82:8080/ws';
  
  // Intervalles
  static const int gpsIntervalSeconds = 10;
  
  // Clés de stockage local
  static const String tokenKey = 'jwt_token';
  static const String userKey = 'current_user';
}
