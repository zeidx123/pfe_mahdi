import 'package:flutter/material.dart';
import 'package:dio/dio.dart';
import '../core/app_theme.dart';
import '../services/tournee_service.dart';
import '../services/auth_service.dart';
import '../widgets/tournee_card.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});
  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  List<dynamic> _tournees = [];
  bool _isLoading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadTournees();
  }

  Future<void> _loadTournees() async {
    try {
      setState(() {
        _isLoading = true;
        _error = null;
      });
      final data = await TourneeService.getMesTournees();
      if (!mounted) return;
      setState(() {
        _tournees = data;
        _isLoading = false;
      });
    } catch (e) {
      if (e is DioException && e.response?.statusCode == 401) {
        await AuthService.logout();
        if (mounted) {
          Navigator.of(context).pushNamedAndRemoveUntil('/login', (_) => false);
        }
        return;
      }
      if (mounted) {
        setState(() {
          _error = 'Impossible de charger vos tournées';
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _logout() async {
    await AuthService.logout();
    if (mounted) {
      Navigator.of(context).pushNamedAndRemoveUntil('/login', (_) => false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final user = AuthService.currentUser;
    final hour = DateTime.now().hour;
    final greeting = hour < 12
        ? 'Bonjour'
        : hour < 18
            ? 'Bon après-midi'
            : 'Bonsoir';
    final firstName = user?['prenom']?.toString().trim();
    final running =
        _tournees.where((tour) => tour['statut'] == 'EN_COURS').length;
    final planned =
        _tournees.where((tour) => tour['statut'] == 'PLANIFIEE').length;

    return Scaffold(
      backgroundColor: GestiTheme.bg,
      appBar: AppBar(
        backgroundColor: Colors.white,
        foregroundColor: GestiTheme.ink,
        elevation: 0,
        scrolledUnderElevation: 0,
        titleSpacing: 20,
        title: Row(children: [
          const Icon(Icons.local_shipping_outlined,
              color: GestiTheme.primaryDark, size: 25),
          const SizedBox(width: 9),
          Text('GestiCollecte',
              style: text.titleMedium?.copyWith(
                  fontWeight: FontWeight.w700, color: GestiTheme.navy)),
        ]),
        actions: [
          IconButton(
              tooltip: 'Assistant',
              icon: const Icon(Icons.chat_bubble_outline_rounded),
              onPressed: () => Navigator.pushNamed(context, '/assistant')),
          IconButton(
              tooltip: 'Actualiser les tournées',
              icon: const Icon(Icons.refresh_rounded),
              onPressed: _isLoading ? null : _loadTournees),
          PopupMenuButton<String>(
            tooltip: 'Plus d’actions',
            icon: const Icon(Icons.more_vert_rounded),
            onSelected: (value) {
              if (value == 'reclamations') {
                Navigator.pushNamed(context, '/reclamations');
              }
              if (value == 'logout') _logout();
            },
            itemBuilder: (_) => const [
              PopupMenuItem(
                  value: 'reclamations',
                  height: 48,
                  child: Text('Réclamations')),
              PopupMenuItem(
                  value: 'logout', height: 48, child: Text('Se déconnecter')),
            ],
          ),
          const SizedBox(width: 8),
        ],
        bottom: const PreferredSize(
            preferredSize: Size.fromHeight(1),
            child: Divider(height: 1, color: GestiTheme.border)),
      ),
      body: SafeArea(
          top: false,
          child: Align(
              alignment: Alignment.topCenter,
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 920),
                child: RefreshIndicator(
                    onRefresh: _loadTournees,
                    child: ListView(
                      physics: const AlwaysScrollableScrollPhysics(),
                      padding: const EdgeInsets.fromLTRB(20, 28, 20, 32),
                      children: [
                        Text(
                            '$greeting${firstName == null || firstName.isEmpty ? '' : ', $firstName'}',
                            style: text.bodyMedium
                                ?.copyWith(color: GestiTheme.muted)),
                        const SizedBox(height: 7),
                        Text('Mes tournées de collecte',
                            style: text.headlineSmall?.copyWith(
                                fontWeight: FontWeight.w700,
                                color: GestiTheme.ink)),
                        const SizedBox(height: 8),
                        Text(
                            'Retrouvez vos affectations et reprenez votre parcours là où vous l’avez laissé.',
                            style: text.bodyMedium?.copyWith(
                                color: GestiTheme.muted, height: 1.55)),
                        if (!_isLoading &&
                            _error == null &&
                            _tournees.isNotEmpty) ...[
                          const SizedBox(height: 20),
                          Wrap(spacing: 10, runSpacing: 10, children: [
                            _CountLabel(
                                label: '$running en cours',
                                active: running > 0),
                            _CountLabel(
                                label:
                                    '$planned planifiée${planned > 1 ? 's' : ''}'),
                          ]),
                        ],
                        const SizedBox(height: 24),
                        if (_isLoading)
                          const Padding(
                              padding: EdgeInsets.symmetric(vertical: 70),
                              child: Center(child: CircularProgressIndicator()))
                        else if (_error != null)
                          _HomeState(
                              icon: Icons.wifi_off_rounded,
                              title: _error!,
                              message:
                                  'Vérifiez votre connexion, puis réessayez. Les visites déjà confirmées restent enregistrées.',
                              action: FilledButton.icon(
                                  onPressed: _loadTournees,
                                  icon: const Icon(Icons.refresh),
                                  label: const Text('Réessayer')))
                        else if (_tournees.isEmpty)
                          _HomeState(
                              icon: Icons.route_outlined,
                              title: 'Aucune tournée planifiée',
                              message:
                                  'Vos tournées apparaîtront ici dès leur affectation par le superviseur.',
                              action: OutlinedButton.icon(
                                  onPressed: _loadTournees,
                                  icon: const Icon(Icons.refresh),
                                  label: const Text('Actualiser')))
                        else
                          ..._tournees.map((tournee) => TourneeCard(
                                tournee: Map<String, dynamic>.from(tournee),
                                isAssigned:
                                    tournee['chauffeurId'] == user?['id'],
                                onTap: () async {
                                  await Navigator.pushNamed(
                                      context, '/tournee-detail',
                                      arguments: tournee);
                                  if (mounted) _loadTournees();
                                },
                              )),
                        const SizedBox(height: 8),
                        Material(
                          color: Colors.white,
                          shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(14),
                              side: const BorderSide(color: GestiTheme.border)),
                          clipBehavior: Clip.antiAlias,
                          child: InkWell(
                              onTap: () =>
                                  Navigator.pushNamed(context, '/assistant'),
                              child: Padding(
                                padding: const EdgeInsets.all(18),
                                child: Row(children: [
                                  Container(
                                      padding: const EdgeInsets.all(11),
                                      decoration: BoxDecoration(
                                          color: GestiTheme.soft,
                                          borderRadius:
                                              BorderRadius.circular(10)),
                                      child: const Icon(
                                          Icons.menu_book_outlined,
                                          color: GestiTheme.primaryDark)),
                                  const SizedBox(width: 14),
                                  Expanded(
                                      child: Column(
                                          crossAxisAlignment:
                                              CrossAxisAlignment.start,
                                          children: [
                                        Text('Une question sur la collecte ?',
                                            style: text.titleSmall?.copyWith(
                                                fontWeight: FontWeight.w600)),
                                        const SizedBox(height: 4),
                                        Text(
                                            'Consultez les consignes et leurs sources avec l’assistant.',
                                            style: text.bodySmall?.copyWith(
                                                color: GestiTheme.muted,
                                                height: 1.5)),
                                      ])),
                                  const SizedBox(width: 8),
                                  const Icon(Icons.chevron_right_rounded,
                                      color: GestiTheme.muted),
                                ]),
                              )),
                        ),
                      ],
                    )),
              ))),
    );
  }
}

class _CountLabel extends StatelessWidget {
  final String label;
  final bool active;
  const _CountLabel({required this.label, this.active = false});
  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
        decoration: BoxDecoration(
            color: active ? GestiTheme.soft : Colors.white,
            border: Border.all(color: GestiTheme.border),
            borderRadius: BorderRadius.circular(8)),
        child: Text(label,
            style: Theme.of(context).textTheme.labelMedium?.copyWith(
                color: active ? GestiTheme.primaryDark : GestiTheme.muted)),
      );
}

class _HomeState extends StatelessWidget {
  final IconData icon;
  final String title;
  final String message;
  final Widget action;
  const _HomeState(
      {required this.icon,
      required this.title,
      required this.message,
      required this.action});
  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 38),
        decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: GestiTheme.border)),
        child: Column(children: [
          Icon(icon, size: 38, color: GestiTheme.primaryDark),
          const SizedBox(height: 18),
          Text(title,
              textAlign: TextAlign.center,
              style: Theme.of(context)
                  .textTheme
                  .titleMedium
                  ?.copyWith(fontWeight: FontWeight.w600)),
          const SizedBox(height: 8),
          ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Text(message,
                  textAlign: TextAlign.center,
                  style: Theme.of(context)
                      .textTheme
                      .bodyMedium
                      ?.copyWith(color: GestiTheme.muted, height: 1.5))),
          const SizedBox(height: 20),
          action,
        ]),
      );
}
