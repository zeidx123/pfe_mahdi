import 'package:flutter/material.dart';
import '../core/app_theme.dart';

class TourneeCard extends StatelessWidget {
  final Map<String, dynamic> tournee;
  final VoidCallback onTap;
  final bool isAssigned;

  const TourneeCard(
      {super.key,
      required this.tournee,
      required this.onTap,
      this.isAssigned = false});

  String _dateLabel(dynamic value) {
    final date = DateTime.tryParse(value?.toString() ?? '');
    if (date == null) return 'Date à confirmer';
    const months = [
      'janv.',
      'févr.',
      'mars',
      'avr.',
      'mai',
      'juin',
      'juil.',
      'août',
      'sept.',
      'oct.',
      'nov.',
      'déc.'
    ];
    return '${date.day} ${months[date.month - 1]} ${date.year}';
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final status = tournee['statut']?.toString();
    final active = status == 'EN_COURS';
    final finished = status == 'TERMINEE';
    final cancelled = status == 'ANNULEE';
    final statusLabel = switch (status) {
      'PLANIFIEE' => 'Planifiée',
      'EN_COURS' => 'En cours',
      'TERMINEE' => 'Terminée',
      'ANNULEE' => 'Annulée',
      _ => 'Statut à confirmer',
    };
    final statusColor = cancelled
        ? Theme.of(context).colorScheme.error
        : active || finished
            ? GestiTheme.primaryDark
            : GestiTheme.muted;
    final visits = (tournee['visits'] as List?) ?? const [];
    final points = (tournee['pointsCollecte'] as List?) ?? const [];
    final count = visits.isNotEmpty ? visits.length : points.length;
    final confirmed = visits
        .where((visit) =>
            visit is Map &&
            visit['legacyProgressMissing'] != true &&
            ['COMPLETED_REPORTED', 'ISSUE_REPORTED'].contains(visit['status']))
        .length;
    final progressKnown = visits.isNotEmpty &&
        !visits.any(
            (visit) => visit is Map && visit['legacyProgressMissing'] == true);
    final name = tournee['nom']?.toString().trim();
    final departure = tournee['heureDepartPrevue']?.toString() ?? '';
    final action = active
        ? 'Reprendre la tournée'
        : finished
            ? 'Consulter le bilan'
            : 'Voir la tournée';

    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Material(
        color: Colors.white,
        shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
            side: BorderSide(
                color: active
                    ? GestiTheme.primary.withOpacity(.45)
                    : GestiTheme.border)),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 18, 20, 0),
            child:
                Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                Expanded(
                    child: Text(
                        tournee['reference']?.toString() ??
                            'Tournée de collecte',
                        style: text.labelMedium?.copyWith(
                            color: GestiTheme.muted, letterSpacing: .3))),
                const SizedBox(width: 8),
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                  decoration: BoxDecoration(
                      color:
                          active || finished ? GestiTheme.soft : GestiTheme.bg,
                      borderRadius: BorderRadius.circular(7)),
                  child: Text(statusLabel,
                      style: text.labelMedium?.copyWith(color: statusColor)),
                ),
              ]),
              const SizedBox(height: 10),
              Text(name == null || name.isEmpty ? 'Tournée de collecte' : name,
                  style: text.titleLarge?.copyWith(
                      fontWeight: FontWeight.w700, color: GestiTheme.ink)),
              const SizedBox(height: 16),
              Wrap(spacing: 18, runSpacing: 10, children: [
                _InfoChip(icon: Icons.place_outlined, label: '$count points'),
                _InfoChip(
                    icon: Icons.calendar_today_outlined,
                    label: _dateLabel(tournee['datePrevue'])),
                if (departure.isNotEmpty)
                  _InfoChip(
                      icon: Icons.schedule_outlined,
                      label: departure.length >= 5
                          ? departure.substring(0, 5)
                          : departure),
                if (isAssigned)
                  const _InfoChip(
                      icon: Icons.person_outline, label: 'Votre affectation'),
              ]),
              if ((active || finished) && progressKnown) ...[
                const SizedBox(height: 18),
                Row(children: [
                  Expanded(
                      child: Text('Points traités',
                          style: text.bodySmall
                              ?.copyWith(color: GestiTheme.muted))),
                  Text('$confirmed / $count',
                      style: text.labelMedium
                          ?.copyWith(color: GestiTheme.primaryDark)),
                ]),
                const SizedBox(height: 8),
                ClipRRect(
                    borderRadius: BorderRadius.circular(4),
                    child: LinearProgressIndicator(
                        value: count > 0 ? confirmed / count : 0,
                        minHeight: 5,
                        backgroundColor: GestiTheme.soft,
                        color: GestiTheme.primary)),
              ],
              const SizedBox(height: 18),
              const Divider(height: 1, color: GestiTheme.border),
              ConstrainedBox(
                  constraints: const BoxConstraints(minHeight: 56),
                  child: Row(children: [
                    Expanded(
                        child: Text(action,
                            style: text.labelLarge
                                ?.copyWith(color: GestiTheme.primaryDark))),
                    const Icon(Icons.arrow_forward_rounded,
                        size: 20, color: GestiTheme.primaryDark),
                  ])),
            ]),
          ),
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
  Widget build(BuildContext context) =>
      Row(mainAxisSize: MainAxisSize.min, children: [
        Icon(icon, size: 17, color: GestiTheme.muted),
        const SizedBox(width: 6),
        Text(label,
            style: Theme.of(context)
                .textTheme
                .bodySmall
                ?.copyWith(color: GestiTheme.muted)),
      ]);
}
