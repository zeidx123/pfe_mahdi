import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:image_picker/image_picker.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'auth_service.dart';
import 'browser_draft_storage_stub.dart'
    if (dart.library.html) 'browser_draft_storage_web.dart' as browser;

/// One manually retried camera draft for the current tour; not an offline queue.
class PhotoDraft {
  final String tourId;
  final String pointId;
  final String stage;
  final String? path;
  final Uint8List? webBytes;
  PhotoDraft(
      {required this.tourId,
      required this.pointId,
      required this.stage,
      this.path,
      this.webBytes});
  Future<Uint8List> bytes() async =>
      webBytes ?? await File(path!).readAsBytes();

  static String imageMimeType(Uint8List bytes) {
    if (bytes.length > 10 * 1024 * 1024) {
      throw const FormatException(
          'La photo dépasse la limite de 10 Mo. Choisissez une image plus petite.');
    }
    if (bytes.length >= 8 &&
        listEquals(bytes.sublist(0, 8),
            [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])) {
      return 'image/png';
    }
    if (bytes.length >= 3 &&
        bytes[0] == 0xff &&
        bytes[1] == 0xd8 &&
        bytes[2] == 0xff) {
      return 'image/jpeg';
    }
    throw const FormatException(
        'Format non pris en charge. Choisissez une photo JPEG ou PNG.');
  }
}

class PhotoDraftService {
  static String get _key => 'photo_draft_${AuthService.currentUser?['id']}';

  static Future<PhotoDraft> save(
      XFile photo, String tourId, String pointId, String stage) async {
    final bytes = await photo.readAsBytes();
    PhotoDraft.imageMimeType(bytes);
    final prefs = await SharedPreferences.getInstance();
    String? path;
    if (!kIsWeb) {
      final dir = await getApplicationDocumentsDirectory();
      path = '${dir.path}/$_key.jpg';
      await File(path).writeAsBytes(bytes, flush: true);
    }
    final data = <String, dynamic>{
      'tourId': tourId,
      'pointId': pointId,
      'stage': stage,
      'path': path,
    };
    if (kIsWeb) {
      try {
        await browser.saveBrowserDraft(_key, {...data, 'bytes': bytes});
        // Remove only the previous base64 representation after durable save.
        await prefs.remove(_key);
      } catch (_) {
        throw const FormatException(
            'Photo non enregistrée : stockage du navigateur indisponible ou plein. Libérez de l’espace et autorisez le stockage local, puis réessayez.');
      }
    } else if (!await prefs.setString(_key, jsonEncode(data))) {
      throw const FormatException(
          'Photo non enregistrée sur cet appareil. Libérez de l’espace puis réessayez.');
    }
    return PhotoDraft(
        tourId: tourId,
        pointId: pointId,
        stage: stage,
        path: path,
        webBytes: kIsWeb ? bytes : null);
  }

  static Future<PhotoDraft?> load() async {
    final prefs = await SharedPreferences.getInstance();
    if (kIsWeb) {
      try {
        final data = await browser.readBrowserDraft(_key);
        if (data != null) {
          return PhotoDraft(
              tourId: data['tourId'],
              pointId: data['pointId'],
              stage: data['stage'],
              webBytes:
                  Uint8List.fromList((data['bytes'] as List).cast<int>()));
        }
      } catch (_) {
        // Storage errors must not prevent loading confirmed server progress.
        // A new capture reports the storage error before claiming it was saved.
      }
    }
    final raw = prefs.getString(_key);
    if (raw == null) return null;
    try {
      final data = jsonDecode(raw);
      if (!kIsWeb && !await File(data['path']).exists()) return null;
      return PhotoDraft(
          tourId: data['tourId'],
          pointId: data['pointId'],
          stage: data['stage'],
          path: data['path'],
          webBytes: kIsWeb ? base64Decode(data['bytes']) : null);
    } catch (_) {
      return null;
    }
  }

  static Future<void> clear() async {
    final draft = await load();
    final prefs = await SharedPreferences.getInstance();
    if (kIsWeb) await browser.removeBrowserDraft(_key);
    await prefs.remove(_key);
    if (draft?.path != null && !kIsWeb) {
      final file = File(draft!.path!);
      if (await file.exists()) await file.delete();
    }
  }
}
