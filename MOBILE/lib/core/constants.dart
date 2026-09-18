import 'package:flutter/foundation.dart';

class AppConstants {
  // Override for a physical device: --dart-define=API_BASE_URL=http://LAN_IP:8080/api
  // The login screen also allows saving the municipality's server address.
  static const String baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue:
        kIsWeb ? 'http://localhost:8080/api' : 'http://10.0.2.2:8080/api',
  );
  static const int gpsIntervalSeconds = 10;
  static const String tokenKey = 'jwt_token';
  static const String userKey = 'current_user';
  static const String serverKey = 'api_base_url';
}
