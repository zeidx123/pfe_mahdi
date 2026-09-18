import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'api_service.dart';
import 'photo_draft_service.dart';

class TourneeService {
  static Future<List<dynamic>> getMesTournees() async {
    final response = await ApiService.instance.get('/tournees/mes-tournees');
    return response.data as List;
  }

  static Future<Map<String, dynamic>> getTourneeById(String id) async {
    final response = await ApiService.instance.get('/tournees/$id');
    return Map<String, dynamic>.from(response.data);
  }

  static Future<Map<String, dynamic>> _action(String path,
      [Map<String, dynamic>? data]) async {
    final response =
        await ApiService.instance.post('/tournees/$path', data: data);
    return Map<String, dynamic>.from(response.data);
  }

  static Future<Map<String, dynamic>> demarrerTournee(String id) =>
      _action('$id/start');
  static Future<Map<String, dynamic>> arrive(String id, String pointId) =>
      _action('$id/points/$pointId/arrive');
  static Future<Map<String, dynamic>> analyse(String id, String pointId) =>
      _action('$id/points/$pointId/analyse');
  static Future<Map<String, dynamic>> complete(
    String id,
    String pointId, {
    required String outcome,
    String? notes,
    String? driverAssessment,
  }) =>
      _action('$id/points/$pointId/complete', {
        'outcome': outcome,
        'notes': notes,
        'driverAssessment': driverAssessment,
      });
  static Future<Map<String, dynamic>> terminerTournee(String id) =>
      _action('$id/finish', {'returnedToDepot': true});

  static Future<bool> draftMatchesStoredPhoto(
      PhotoDraft draft, Map<String, dynamic> tour) async {
    if (draft.tourId != tour['id'] ||
        !['AFTER', 'BEFORE'].contains(draft.stage)) return false;
    final visits = tour['visits'];
    if (visits is! List) return false;
    final matches =
        visits.whereType<Map>().where((v) => v['pointId'] == draft.pointId);
    if (matches.isEmpty) return false;
    final path =
        matches.first[draft.stage == 'AFTER' ? 'afterPhoto' : 'beforePhoto'];
    if (path is! String || path.isEmpty) return false;
    try {
      // Point/stage alone is insufficient: a replacement photo may still be
      // unsent. Only identical bytes establish that this draft is on the server.
      final response = await ApiService.instance.get<List<int>>(
          ApiService.protectedFileUrl(path),
          options: Options(responseType: ResponseType.bytes));
      return listEquals(await draft.bytes(), response.data);
    } catch (_) {
      return false;
    }
  }

  static Future<Map<String, dynamic>> upload(PhotoDraft draft) async {
    final bytes = await draft.bytes();
    final mimeType = PhotoDraft.imageMimeType(bytes);
    final response = await ApiService.instance.post(
      '/tournees/${draft.tourId}/points/${draft.pointId}/photo',
      data: FormData.fromMap({
        'stage': draft.stage,
        'photo': MultipartFile.fromBytes(bytes,
            filename:
                '${draft.stage.toLowerCase()}.${mimeType == 'image/png' ? 'png' : 'jpg'}',
            contentType: DioMediaType.parse(mimeType))
      }),
    );
    return Map<String, dynamic>.from(response.data);
  }
}
