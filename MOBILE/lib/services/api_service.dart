import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../core/constants.dart';

class ApiService {
  static final ValueNotifier<bool> sessionExpired = ValueNotifier(false);
  static bool _initialized = false;
  static final Dio _dio = Dio(BaseOptions(
    baseUrl: AppConstants.baseUrl,
    connectTimeout: const Duration(seconds: 10),
    receiveTimeout: const Duration(seconds: 60),
    sendTimeout: const Duration(seconds: 40),
    headers: {'Content-Type': 'application/json'},
  ));

  static Future<void> init() async {
    final prefs = await SharedPreferences.getInstance();
    _dio.options.baseUrl =
        prefs.getString(AppConstants.serverKey) ?? AppConstants.baseUrl;
    if (_initialized) return;
    _initialized = true;
    _dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) async {
        final prefs = await SharedPreferences.getInstance();
        final token = prefs.getString(AppConstants.tokenKey);
        // Never forward the municipality token to another origin.
        if (token != null &&
            options.uri.origin == Uri.parse(_dio.options.baseUrl).origin) {
          options.headers['Authorization'] = 'Bearer $token';
        }
        handler.next(options);
      },
      onError: (error, handler) async {
        if (error.response?.statusCode == 401 &&
            !error.requestOptions.path.endsWith('/auth/login')) {
          final prefs = await SharedPreferences.getInstance();
          await prefs.remove(AppConstants.tokenKey);
          await prefs.remove(AppConstants.userKey);
          sessionExpired.value = true;
        }
        // A forbidden action is not an expired session.
        handler.next(error);
      },
    ));
  }

  static Future<void> configureServer(String value) async {
    final uri = Uri.tryParse(value.trim());
    if (uri == null ||
        !['http', 'https'].contains(uri.scheme) ||
        uri.host.isEmpty ||
        uri.userInfo.isNotEmpty ||
        uri.hasQuery ||
        uri.hasFragment) {
      throw const FormatException(
          'Adresse HTTP(S) valide requise, par exemple http://192.168.1.10:8080/api');
    }
    final url = value.trim().replaceFirst(RegExp(r'/+$'), '');
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(AppConstants.serverKey, url);
    await prefs.remove(AppConstants.tokenKey);
    await prefs.remove(AppConstants.userKey);
    _dio.options.baseUrl = url;
  }

  static String protectedFileUrl(String value) {
    final base = Uri.parse(_dio.options.baseUrl);
    final normalized = value.startsWith('/') || value.startsWith('http')
        ? value
        : '/uploads/photos/$value';
    final uri = base.resolve(normalized);
    if (uri.origin != base.origin || !uri.path.startsWith('/uploads/')) {
      throw const FormatException('Adresse de document non autorisée');
    }
    return uri.toString();
  }

  static String errorMessage(Object error) {
    if (error is DioException) {
      final data = error.response?.data;
      if (data is Map &&
          data['message'] is String &&
          (data['message'] as String).isNotEmpty) {
        return data['message'] as String;
      }
      if (error.response?.statusCode == 401) {
        return 'Session expirée. Reconnectez-vous.';
      }
      if (error.response?.statusCode == 403) {
        return 'Vous ne pouvez pas accéder à cette ressource.';
      }
      if ([
        DioExceptionType.connectionTimeout,
        DioExceptionType.receiveTimeout,
        DioExceptionType.sendTimeout,
        DioExceptionType.connectionError
      ].contains(error.type)) {
        return 'Connexion interrompue. Actualisez pour vérifier les données enregistrées, puis réessayez.';
      }
      return 'Action non enregistrée. Réessayez ou contactez votre superviseur.';
    }
    if (error is FormatException) return error.message;
    return 'Action impossible : vérifiez vos permissions et réessayez.';
  }

  static Dio get instance => _dio;
}
