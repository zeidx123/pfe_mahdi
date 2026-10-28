import 'package:latlong2/latlong.dart';

class TourProgress {
  final Map<String, dynamic> tour;
  TourProgress(this.tour);

  List<Map<String, dynamic>> get visits => ((tour['visits'] as List?) ?? [])
      .whereType<Map>()
      .map((v) => Map<String, dynamic>.from(v))
      .toList()
    ..sort((a, b) =>
        ((a['order'] as num?) ?? 0).compareTo((b['order'] as num?) ?? 0));

  static bool isHandled(Map<String, dynamic> visit) =>
      ['COMPLETED_REPORTED', 'ISSUE_REPORTED'].contains(visit['status']);

  Map<String, dynamic>? get currentVisit {
    for (final visit in visits) {
      if (!isHandled(visit)) return visit;
    }
    return null;
  }

  Map<String, dynamic> point(String id) => ((tour['pointsCollecte'] as List?) ??
          [])
      .whereType<Map>()
      .map((p) => Map<String, dynamic>.from(p))
      .firstWhere((p) => p['id']?.toString() == id, orElse: () => {'id': id});

  Map<String, dynamic>? get route =>
      tour['route'] is Map ? Map<String, dynamic>.from(tour['route']) : null;

  Map<String, dynamic>? get destination {
    final visit = currentVisit;
    if (visit != null) return point(visit['pointId'].toString());
    return route?['depot'] is Map
        ? Map<String, dynamic>.from(route!['depot'])
        : null;
  }

  static LatLng? coordinate(Map<String, dynamic>? point) {
    final lat = double.tryParse('${point?['latitude']}');
    final lon = double.tryParse('${point?['longitude']}');
    if (lat == null ||
        lon == null ||
        !lat.isFinite ||
        !lon.isFinite ||
        lat < -90 ||
        lat > 90 ||
        lon < -180 ||
        lon > 180) return null;
    return LatLng(lat, lon);
  }

  List<LatLng> get geometry => ((route?['geometry'] as List?) ?? [])
      .whereType<List>()
      .where((pair) => pair.length >= 2)
      .map((pair) => coordinate({'latitude': pair[1], 'longitude': pair[0]}))
      .whereType<LatLng>()
      .toList();

  bool get allHandled => visits.isNotEmpty && visits.every(isHandled);

  double? get remainingTravelSeconds {
    final legs = route?['legs'];
    if (legs is! List) return null;
    final done = visits.where(isHandled).length;
    final reachedCurrent = currentVisit?['status'] == 'ARRIVED' ? 1 : 0;
    return legs.skip(done + reachedCurrent).fold<double>(
        0,
        (sum, leg) =>
            sum + ((leg['durationSeconds'] as num?)?.toDouble() ?? 0));
  }

  double? get remainingServiceSeconds {
    if (route == null) return null;
    return visits.where((v) => !isHandled(v)).fold<double>(
        0,
        (sum, v) =>
            sum + ((v['plannedServiceSeconds'] as num?)?.toDouble() ?? 0));
  }

  static Uri navigationUri(LatLng destination) =>
      Uri.https('www.google.com', '/maps/dir/', {
        'api': '1',
        'destination': '${destination.latitude},${destination.longitude}',
        'travelmode': 'driving',
        'dir_action': 'navigate',
      });

  static const assessmentLabels = {
    'CLEANED': 'Le point apparaît nettoyé',
    'NOT_CLEANED': 'Le point ne paraît pas nettoyé',
    'OBSTRUCTED': 'Point obstrué',
    'CANNOT_DETERMINE': 'Impossible à déterminer',
    'ANALYSIS_UNAVAILABLE': 'Analyse indisponible',
  };

  static String assessmentLabel(dynamic value) =>
      assessmentLabels[value] ?? 'Aucune analyse disponible';

  // Local vision engine only; these fields are absent for LLM analyses.
  static const qualityIssueLabels = {
    'TROP_SOMBRE': 'trop sombre',
    'SUREXPOSEE': 'surexposée',
    'FLOUE': 'floue',
  };

  /// « propre 91 % · débordement 7 % · indéterminé 2 % », or null without probabilities.
  static String? probabilitiesText(dynamic probabilities) {
    if (probabilities is! Map) return null;
    final parts = <String>[];
    for (final entry in const {
      'PROPRE': 'propre',
      'DEBORDEMENT': 'débordement',
      'CANNOT_DETERMINE': 'indéterminé',
    }.entries) {
      final value = probabilities[entry.key];
      if (value is! num) return null;
      parts.add('${entry.value} ${(value * 100).round()} %');
    }
    return parts.join(' · ');
  }

  static String? retakeMessage(dynamic qualityIssue) {
    final reason = qualityIssueLabels[qualityIssue];
    return reason == null ? null : 'Photo à refaire : $reason';
  }

  /// A_VERIFIER only orders the supervisor's review; it never blocks the visit.
  static bool needsSupervisorReview(Map<String, dynamic>? visit) =>
      visit?['analysisDecision'] == 'A_VERIFIER';

  static String duration(dynamic seconds) =>
      seconds is num ? '${(seconds / 60).ceil()} min' : 'Non disponible';
}
