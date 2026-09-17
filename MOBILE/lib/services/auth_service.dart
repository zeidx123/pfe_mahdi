import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../core/constants.dart';
import 'api_service.dart';

class AuthService {
  static Map<String, dynamic>? _currentUser;

  static Map<String, dynamic>? get currentUser => _currentUser;

  static Future<bool> login(String email, String password) async {
    try {
      final response = await ApiService.instance.post(
        '/auth/login',
        data: {'email': email, 'password': password},
      );
      final token = response.data['token'];
      final user = response.data['user'];

      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(AppConstants.tokenKey, token);
      await prefs.setString(AppConstants.userKey, jsonEncode(user));
      _currentUser = user;
      return true;
    } catch (e) {
      return false;
    }
  }

  static Future<void> logout() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(AppConstants.tokenKey);
    await prefs.remove(AppConstants.userKey);
    _currentUser = null;
  }

  static Future<bool> isLoggedIn() async {
    final prefs = await SharedPreferences.getInstance();
    final hasToken = prefs.containsKey(AppConstants.tokenKey);
    if (hasToken && _currentUser == null) {
      final userStr = prefs.getString(AppConstants.userKey);
      if (userStr != null) {
        try {
          _currentUser = jsonDecode(userStr);
        } catch (_) {}
      }
    }
    return hasToken;
  }
}
