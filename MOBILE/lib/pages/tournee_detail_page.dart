import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import '../services/tournee_service.dart';
import '../services/tracking_service.dart';
import 'active_tracking_page.dart';

class TourneeDetailPage extends StatefulWidget {
  const TourneeDetailPage({super.key});

  @override
  State<TourneeDetailPage> createState() => _TourneeDetailPageState();
}

class _TourneeDetailPageState extends State<TourneeDetailPage> {
  bool _isStarting = false;

  Future<void> _handleDemarrer(Map<String, dynamic> tournee) async {
    setState(() => _isStarting = true);
    try {
      // 1. Changer le statut côté serveur
      await TourneeService.demarrerTournee(tournee['id']);

      // 2. Démarrer le suivi GPS depuis le téléphone
      await TrackingService.startTracking(
        camionId: tournee['camionId'] ?? 'CAM-UNKNOWN',
        camionMatricule: tournee['camionMatricule'] ?? 'N/A',
        tourneeId: tournee['id'],
        tourneeReference: tournee['reference'] ?? '',
      );

      // 3. Naviguer vers l'écran de collecte actif
      if (mounted) {
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(
            builder: (_) => ActiveTrackingPage(tournee: tournee),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Erreur: ${e.toString()}'), backgroundColor: Colors.red),
        );
      }
      setState(() => _isStarting = false);
    }
  }

  Color _getStatusColor(String? statut) {
    switch (statut) {
      case 'PLANIFIEE': return Colors.blue;
      case 'EN_COURS': return Colors.orange;
      case 'TERMINEE': return Colors.green;
      default: return Colors.grey;
    }
  }

  @override
  Widget build(BuildContext context) {
    final tournee = ModalRoute.of(context)!.settings.arguments as Map<String, dynamic>;
    final points = (tournee['pointsCollecte'] as List?) ?? [];
    final statut = tournee['statut'] as String?;

    return Scaffold(
      backgroundColor: const Color(0xFFF1F5F9),
      appBar: AppBar(
        backgroundColor: const Color(0xFF0369a1),
        foregroundColor: Colors.white,
        title: Text(tournee['nom'] ?? 'Détail Tournée'),
        elevation: 0,
      ),
      body: Column(
        children: [
          // Header info
          Container(
            color: const Color(0xFF0369a1),
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
            child: Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: Colors.white.withOpacity(0.15),
                borderRadius: BorderRadius.circular(16),
              ),
              child: Column(
                children: [
                  _InfoRow(icon: Icons.tag, label: 'Référence', value: tournee['reference'] ?? ''),
                  const SizedBox(height: 12),
                  _InfoRow(icon: Icons.local_shipping, label: 'Camion', value: tournee['camionMatricule'] ?? 'N/A'),
                  const SizedBox(height: 12),
                  _InfoRow(icon: Icons.calendar_today, label: 'Date prévue', value: tournee['datePrevue'] ?? ''),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      const Icon(Icons.circle, size: 14, color: Colors.white70),
                      const SizedBox(width: 8),
                      const Text('Statut', style: TextStyle(color: Colors.white70, fontSize: 13)),
                      const Spacer(),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                        decoration: BoxDecoration(
                          color: _getStatusColor(statut),
                          borderRadius: BorderRadius.circular(20),
                        ),
                        child: Text(statut ?? '', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700, fontSize: 12)),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),

          // Carte des points de collecte
          if (points.isNotEmpty)
            Container(
              height: 220,
              margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(16),
                boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.08), blurRadius: 10, offset: const Offset(0, 4))],
              ),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(16),
                child: FlutterMap(
                  options: MapOptions(
                    initialCenter: LatLng(
                      points.first['latitude'] ?? 36.8065, // Tunis par défaut
                      points.first['longitude'] ?? 10.1815,
                    ),
                    initialZoom: 13,
                  ),
                  children: [
                    TileLayer(
                      urlTemplate: 'https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png',
                      subdomains: const ['a', 'b', 'c', 'd'],
                      userAgentPackageName: 'com.municipalite.gesticollecte_mobile',
                    ),
                    MarkerLayer(
                      markers: points.asMap().entries.where((e) => e.value['latitude'] != null).map((e) {
                        final idx = e.key;
                        final p = e.value;
                        return Marker(
                          point: LatLng(p['latitude'], p['longitude']),
                          width: 40,
                          height: 40,
                          child: Stack(
                            alignment: Alignment.center,
                            children: [
                              const Icon(Icons.location_pin, color: Colors.red, size: 38),
                              Positioned(
                                top: 5,
                                child: Container(
                                  padding: const EdgeInsets.all(2),
                                  decoration: const BoxDecoration(color: Colors.white, shape: BoxShape.circle),
                                  constraints: const BoxConstraints(minWidth: 16, minHeight: 16),
                                  child: Center(
                                    child: Text(
                                      '${idx + 1}',
                                      style: const TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: Colors.red),
                                    ),
                                  ),
                                ),
                              ),
                            ],
                          ),
                        );
                      }).toList(),
                    ),
                  ],
                ),
              ),
            ),

          // Points de collecte
          Expanded(
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                const Padding(
                  padding: EdgeInsets.only(bottom: 12),
                  child: Text('Points de collecte', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700, color: Color(0xFF0f172a))),
                ),
                if (points.isEmpty)
                  Center(
                    child: Text('Aucun point de collecte', style: TextStyle(color: Colors.grey.shade500)),
                  )
                else
                  ...points.asMap().entries.map((e) {
                    final point = e.value;
                    return Container(
                      margin: const EdgeInsets.only(bottom: 10),
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(14),
                        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.04), blurRadius: 8)],
                      ),
                      child: Row(
                        children: [
                          Container(
                            width: 36, height: 36,
                            decoration: BoxDecoration(color: const Color(0xFFE0F2FE), shape: BoxShape.circle),
                            child: Center(child: Text('${e.key + 1}', style: const TextStyle(fontWeight: FontWeight.w800, color: Color(0xFF0369a1)))),
                          ),
                          const SizedBox(width: 14),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(point['reference'] ?? '', style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14)),
                                Text(point['adresse'] ?? '', style: TextStyle(fontSize: 12, color: Colors.grey.shade500)),
                              ],
                            ),
                          ),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                            decoration: BoxDecoration(color: const Color(0xFFF0FDF4), borderRadius: BorderRadius.circular(8)),
                            child: Text('${point['niveauRemplissage'] ?? 0}%', style: const TextStyle(color: Color(0xFF16a34a), fontWeight: FontWeight.w700, fontSize: 12)),
                          ),
                        ],
                      ),
                    );
                  }),
              ],
            ),
          ),
        ],
      ),
      bottomNavigationBar: statut == 'PLANIFIEE'
          ? SafeArea(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: SizedBox(
                  height: 56,
                  child: ElevatedButton.icon(
                    onPressed: _isStarting ? null : () => _handleDemarrer(tournee),
                    icon: _isStarting
                        ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2))
                        : const Icon(Icons.play_arrow_rounded),
                    label: Text(_isStarting ? 'Démarrage...' : 'Démarrer la tournée', style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF16a34a),
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                      elevation: 0,
                    ),
                  ),
                ),
              ),
            )
          : null,
    );
  }
}

class _InfoRow extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  const _InfoRow({required this.icon, required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, size: 16, color: Colors.white70),
        const SizedBox(width: 8),
        Text(label, style: const TextStyle(color: Colors.white70, fontSize: 13)),
        const Spacer(),
        Text(value, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600, fontSize: 13)),
      ],
    );
  }
}
