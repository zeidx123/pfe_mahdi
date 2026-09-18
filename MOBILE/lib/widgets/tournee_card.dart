import 'package:flutter/material.dart';

class TourneeCard extends StatelessWidget {
  final Map<String, dynamic> tournee;
  final VoidCallback onTap;
  final bool isAssigned;

  const TourneeCard({
    super.key,
    required this.tournee,
    required this.onTap,
    this.isAssigned = false,
  });

  Color _statusColor(String? statut) {
    switch (statut) {
      case 'PLANIFIEE':
        return Colors.blue;
      case 'EN_COURS':
        return Colors.orange;
      case 'TERMINEE':
        return Colors.green;
      case 'ANNULEE':
        return Colors.red;
      default:
        return Colors.grey;
    }
  }

  IconData _statusIcon(String? statut) {
    switch (statut) {
      case 'PLANIFIEE':
        return Icons.schedule;
      case 'EN_COURS':
        return Icons.play_circle_filled;
      case 'TERMINEE':
        return Icons.check_circle;
      case 'ANNULEE':
        return Icons.cancel;
      default:
        return Icons.help;
    }
  }

  String _statusLabel(String? statut) {
    switch (statut) {
      case 'PLANIFIEE':
        return 'Planifiée';
      case 'EN_COURS':
        return 'En cours';
      case 'TERMINEE':
        return 'Terminée';
      case 'ANNULEE':
        return 'Annulée';
      default:
        return statut ?? '';
    }
  }

  @override
  Widget build(BuildContext context) {
    final statut = tournee['statut'] as String?;
    final color = _statusColor(statut);
    final pointsCount = (tournee['pointsCollecte'] as List?)?.length ?? 0;

    return GestureDetector(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.only(bottom: 12),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(18),
          boxShadow: [
            BoxShadow(
                color: Colors.black.withOpacity(0.05),
                blurRadius: 10,
                offset: const Offset(0, 4))
          ],
        ),
        child: Column(
          children: [
            // Header coloré
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: color.withOpacity(0.08),
                borderRadius:
                    const BorderRadius.vertical(top: Radius.circular(18)),
              ),
              child: Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                        color: color.withOpacity(0.15), shape: BoxShape.circle),
                    child: Icon(Icons.local_shipping_rounded,
                        color: color, size: 22),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            if (isAssigned) ...[
                              const Icon(Icons.star_rounded,
                                  color: Colors.amber, size: 20),
                              const SizedBox(width: 4),
                            ],
                            Expanded(
                              child: Text(
                                tournee['nom'] ?? '',
                                style: const TextStyle(
                                    fontWeight: FontWeight.w700, fontSize: 15),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                          ],
                        ),
                        Text(tournee['reference'] ?? '',
                            style: TextStyle(
                                fontSize: 12, color: Colors.grey.shade500)),
                      ],
                    ),
                  ),
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                    decoration: BoxDecoration(
                        color: color.withOpacity(0.15),
                        borderRadius: BorderRadius.circular(20)),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(_statusIcon(statut), size: 12, color: color),
                        const SizedBox(width: 4),
                        Text(_statusLabel(statut),
                            style: TextStyle(
                                color: color,
                                fontWeight: FontWeight.w700,
                                fontSize: 11)),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            // Infos bas
            Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  _InfoChip(
                      icon: Icons.location_pin, label: '$pointsCount points'),
                  const SizedBox(width: 12),
                  _InfoChip(
                      icon: Icons.calendar_today,
                      label: tournee['datePrevue'] ?? '-'),
                  const Spacer(),
                  const Icon(Icons.arrow_forward_ios_rounded,
                      size: 14, color: Color(0xFF94a3b8)),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _InfoChip extends StatelessWidget {
  final IconData icon;
  final String label;
  const _InfoChip({required this.icon, required this.label});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 13, color: Colors.grey.shade400),
        const SizedBox(width: 4),
        Text(label,
            style: TextStyle(fontSize: 12, color: Colors.grey.shade600)),
      ],
    );
  }
}
