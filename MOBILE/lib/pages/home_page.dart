import 'package:flutter/material.dart';
import 'package:dio/dio.dart';
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
      if (e is DioException && (e.response?.statusCode == 401)) {
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

  @override
  Widget build(BuildContext context) {
    final user = AuthService.currentUser;
    final now = DateTime.now();
    final greeting = now.hour < 12
        ? 'Bonjour'
        : now.hour < 18
            ? 'Bon après-midi'
            : 'Bonsoir';

    return Scaffold(
      backgroundColor: const Color(0xFFF1F5F9),
      appBar: AppBar(
        backgroundColor: const Color(0xFF0369a1),
        foregroundColor: Colors.white,
        elevation: 0,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('$greeting, ${user?['prenom'] ?? 'Chauffeur'}',
                style:
                    const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
            const Text('Mes tournées de collecte',
                style: TextStyle(fontSize: 12, fontWeight: FontWeight.w400)),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.smart_toy_rounded),
            tooltip: 'Assistant',
            onPressed: () {
              Navigator.pushNamed(context, '/assistant');
            },
          ),
          IconButton(
            icon: const Icon(Icons.chat_bubble_outline_rounded),
            tooltip: 'Réclamations',
            onPressed: () {
              Navigator.pushNamed(context, '/reclamations');
            },
          ),
          IconButton(
            icon: const Icon(Icons.logout_rounded),
            onPressed: () async {
              await AuthService.logout();
              if (context.mounted) {
                Navigator.of(context)
                    .pushNamedAndRemoveUntil('/login', (_) => false);
              }
            },
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _loadTournees,
        child: _isLoading
            ? const Center(
                child: CircularProgressIndicator(color: Color(0xFF0369a1)))
            : _error != null
                ? Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.wifi_off,
                            size: 60, color: Colors.grey.shade400),
                        const SizedBox(height: 16),
                        Text(_error!,
                            style: TextStyle(color: Colors.grey.shade600)),
                        const SizedBox(height: 12),
                        ElevatedButton(
                            onPressed: _loadTournees,
                            child: const Text('Réessayer')),
                      ],
                    ),
                  )
                : _tournees.isEmpty
                    ? Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(Icons.check_circle_outline,
                                size: 80, color: Colors.green.shade300),
                            const SizedBox(height: 16),
                            const Text('Aucune tournée planifiée',
                                style: TextStyle(
                                    fontSize: 16, fontWeight: FontWeight.w600)),
                            Text('Profitez de votre journée !',
                                style: TextStyle(color: Colors.grey.shade500)),
                          ],
                        ),
                      )
                    : ListView.builder(
                        padding: const EdgeInsets.all(16),
                        itemCount: _tournees.length,
                        itemBuilder: (context, index) {
                          final tournee = _tournees[index];
                          final isAssigned =
                              tournee['chauffeurId'] == user?['id'];
                          return TourneeCard(
                            tournee: tournee,
                            isAssigned: isAssigned,
                            onTap: () async {
                              await Navigator.pushNamed(
                                context,
                                '/tournee-detail',
                                arguments: tournee,
                              );
                              if (mounted) _loadTournees();
                            },
                          );
                        },
                      ),
      ),
    );
  }
}
