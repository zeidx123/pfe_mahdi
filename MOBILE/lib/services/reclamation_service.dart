import 'api_service.dart';

class ReclamationService {
  static Future<List<dynamic>> getMyReclamations() async {
    final response =
        await ApiService.instance.get('/reclamations/mes-reclamations');
    return response.data;
  }

  static Future<Map<String, dynamic>> createReclamation(
      String titre, String type, String description) async {
    final response = await ApiService.instance.post(
      '/reclamations',
      data: {
        'titre': titre,
        'type': type,
        'description': description,
      },
    );
    return response.data;
  }
}
