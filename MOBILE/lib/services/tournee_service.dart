import 'api_service.dart';

class TourneeService {
  static Future<List<dynamic>> getMesTournees() async {
    final response = await ApiService.instance.get('/tournees/mes-tournees');
    return response.data;
  }

  static Future<List<dynamic>> getAllTournees() async {
    final response = await ApiService.instance.get('/tournees');
    return response.data;
  }

  static Future<Map<String, dynamic>> getTourneeById(String id) async {
    final response = await ApiService.instance.get('/tournees/$id');
    return response.data;
  }

  static Future<void> demarrerTournee(String id) async {
    await ApiService.instance.patch('/tournees/$id/statut?statut=EN_COURS');
  }

  static Future<void> terminerTournee(String id) async {
    await ApiService.instance.patch('/tournees/$id/statut?statut=TERMINEE');
  }
}
