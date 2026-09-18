import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../core/constants.dart';
import 'api_service.dart';
import 'tracking_service.dart';

class AuthService {
  static Map<String, dynamic>? _currentUser;
  static String? lastError;
  static Map<String, dynamic>? get currentUser => _currentUser;

  static Map<String, dynamic> userFromLogin(Map<String, dynamic> data) {
    // Spring's AuthResponse is flat. Accept the old nested representation too.
    final raw =
        data['user'] is Map ? Map<String, dynamic>.from(data['user']) : data;
    if (raw['id'] is! String ||
        (raw['id'] as String).isEmpty ||
        raw['role'] != 'CHAUFFEUR') {
      throw const FormatException(
          'Cette application est réservée aux chauffeurs.');
    }
    return {
      for (final key in ['id', 'email', 'nom', 'prenom', 'role', 'photo'])
        key: raw[key]
    };
  }

  static Future<bool> login(String email, String password) async {
    lastError = null;
    try {
      final response = await ApiService.instance
          .post('/auth/login', data: {'email': email, 'password': password});
      final data = Map<String, dynamic>.from(response.data);
      final token = data['token'];
      final user = userFromLogin(data);
      if (token is! String || token.isEmpty) {
        throw const FormatException('Réponse de connexion incomplète.');
      }
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(AppConstants.tokenKey, token);
      await prefs.setString(AppConstants.userKey, jsonEncode(user));
      _currentUser = user;
      ApiService.sessionExpired.value = false;
      return true;
    } catch (error) {
      lastError = error is DioException &&
              [401, 403].contains(error.response?.statusCode)
          ? 'Identifiants incorrects ou compte désactivé.'
          : ApiService.errorMessage(error);
      return false;
    }
  }

  static Future<void> logout() async {
    await TrackingService.stopTracking();
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(AppConstants.tokenKey);
    await prefs.remove(AppConstants.userKey);
    _currentUser = null;
  }

  static Future<bool> isLoggedIn() async {
    final prefs = await SharedPreferences.getInstance();
    final token = prefs.getString(AppConstants.tokenKey);
    final userStr = prefs.getString(AppConstants.userKey);
    if (token == null || token.isEmpty || userStr == null) {
      await logout();
      return false;
    }
    try {
      _currentUser =
          userFromLogin(Map<String, dynamic>.from(jsonDecode(userStr)));
      final parts = token.split('.');
      if (parts.length != 3) throw const FormatException('Jeton invalide');
      final claims = jsonDecode(
          utf8.decode(base64Url.decode(base64Url.normalize(parts[1]))));
      final exp = claims['exp'];
      if (exp is num && DateTime.now().millisecondsSinceEpoch >= exp * 1000) {
        throw const FormatException('Session expirée');
      }
      return true;
    } catch (_) {
      await logout();
      return false;
    }
  }
}
