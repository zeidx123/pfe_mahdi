import 'package:flutter/material.dart';
import '../services/reclamation_service.dart';

class ReclamationsPage extends StatefulWidget {
  const ReclamationsPage({super.key});

  @override
  State<ReclamationsPage> createState() => _ReclamationsPageState();
}

class _ReclamationsPageState extends State<ReclamationsPage> {
  List<dynamic> _reclamations = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadReclamations();
  }

  Future<void> _loadReclamations() async {
    try {
      setState(() => _isLoading = true);
      final list = await ReclamationService.getMyReclamations();
      // Trier par date décroissante
      list.sort((a, b) {
        final da = a['dateCreation'] ?? '';
        final db = b['dateCreation'] ?? '';
        return db.compareTo(da);
      });
      setState(() {
        _reclamations = list;
        _isLoading = false;
      });
    } catch (e) {
      setState(() => _isLoading = false);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Erreur lors du chargement : $e')),
        );
      }
    }
  }

  void _ouvrirCreationDialog() {
    final titreController = TextEditingController();
    final descController = TextEditingController();
    String typeSelectionne = 'PANNE_MECANIQUE';

    final types = {
      'PANNE_MECANIQUE': 'Panne mécanique',
      'ROUTE_BLOQUEE': 'Route bloquée',
      'BAC_DEGRADE': 'Bac dégradé / manquant',
      'AUTRE': 'Autre',
    };

    showDialog(
      context: context,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            return AlertDialog(
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
              title: const Text('Déposer une réclamation', style: TextStyle(fontWeight: FontWeight.bold)),
              content: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    DropdownButtonFormField<String>(
                      value: typeSelectionne,
                      decoration: const InputDecoration(labelText: 'Type de problème'),
                      items: types.entries.map((e) {
                        return DropdownMenuItem(value: e.key, child: Text(e.value));
                      }).toList(),
                      onChanged: (val) {
                        if (val != null) {
                          setDialogState(() => typeSelectionne = val);
                        }
                      },
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: titreController,
                      decoration: const InputDecoration(
                        labelText: 'Sujet / Titre',
                        hintText: 'Ex: Panne moteur, Bac introuvable...',
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: descController,
                      maxLines: 4,
                      decoration: const InputDecoration(
                        labelText: 'Description détaillée',
                        hintText: 'Expliquez clairement le problème...',
                        border: OutlineInputBorder(),
                      ),
                    ),
                  ],
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(context),
                  child: const Text('Annuler', style: TextStyle(color: Colors.grey)),
                ),
                ElevatedButton(
                  style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFF0369a1)),
                  onPressed: () async {
                    final titre = titreController.text.trim();
                    final desc = descController.text.trim();
                    if (titre.isEmpty || desc.isEmpty) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Veuillez remplir tous les champs')),
                      );
                      return;
                    }
                    try {
                      await ReclamationService.createReclamation(titre, typeSelectionne, desc);
                      Navigator.pop(context);
                      _loadReclamations();
                    } catch (e) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(content: Text('Erreur d\'envoi : $e')),
                      );
                    }
                  },
                  child: const Text('Envoyer', style: TextStyle(color: Colors.white)),
                ),
              ],
            );
          },
        );
      },
    );
  }

  String _formatType(String? type) {
    switch (type) {
      case 'PANNE_MECANIQUE':
        return '⚙️ Panne mécanique';
      case 'ROUTE_BLOQUEE':
        return '🚧 Route bloquée';
      case 'BAC_DEGRADE':
        return '🗑️ Bac dégradé / manquant';
      default:
        return '📝 Autre';
    }
  }

  String _formatDate(String? dateStr) {
    if (dateStr == null) return '';
    try {
      final parsed = DateTime.parse(dateStr);
      return '${parsed.day.toString().padLeft(2, '0')}/${parsed.month.toString().padLeft(2, '0')}/${parsed.year} à ${parsed.hour.toString().padLeft(2, '0')}:${parsed.minute.toString().padLeft(2, '0')}';
    } catch (_) {
      return dateStr;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF1F5F9),
      appBar: AppBar(
        backgroundColor: const Color(0xFF0369a1),
        foregroundColor: Colors.white,
        title: const Text('Mes Réclamations', style: TextStyle(fontWeight: FontWeight.bold)),
      ),
      body: RefreshIndicator(
        onRefresh: _loadReclamations,
        child: _isLoading
            ? const Center(child: CircularProgressIndicator(color: Color(0xFF0369a1)))
            : _reclamations.isEmpty
                ? ListView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    children: [
                      SizedBox(height: MediaQuery.of(context).size.height * 0.3),
                      const Center(
                        child: Column(
                          children: [
                            Icon(Icons.mark_chat_unread_outlined, size: 70, color: Colors.grey),
                            SizedBox(height: 16),
                            Text('Aucune réclamation déposée', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
                            Text('Cliquez sur le bouton + pour en signaler une.', style: TextStyle(color: Colors.grey)),
                          ],
                        ),
                      ),
                    ],
                  )
                : ListView.builder(
                    padding: const EdgeInsets.all(16),
                    itemCount: _reclamations.length,
                    itemBuilder: (context, index) {
                      final r = _reclamations[index];
                      final isTraitee = r['statut'] == 'TRAITEE';
                      return Card(
                        margin: const EdgeInsets.only(bottom: 12),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                        elevation: 2,
                        child: Padding(
                          padding: const EdgeInsets.all(16),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  Text(
                                    _formatType(r['type']),
                                    style: const TextStyle(fontWeight: FontWeight.bold, color: Color(0xFF0369a1), fontSize: 13),
                                  ),
                                  const Spacer(),
                                  Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                    decoration: BoxDecoration(
                                      color: isTraitee ? Colors.green.shade100 : Colors.orange.shade100,
                                      borderRadius: BorderRadius.circular(12),
                                    ),
                                    child: Text(
                                      isTraitee ? 'Traitée' : 'En attente',
                                      style: TextStyle(
                                        color: isTraitee ? Colors.green.shade800 : Colors.orange.shade800,
                                        fontSize: 11,
                                        fontWeight: FontWeight.bold,
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 8),
                              Text(r['titre'] ?? '', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                              const SizedBox(height: 4),
                              Text(_formatDate(r['dateCreation']), style: const TextStyle(fontSize: 11, color: Colors.grey)),
                              const Divider(height: 20),
                              Text(r['description'] ?? '', style: const TextStyle(fontSize: 14)),
                              if (isTraitee && r['reponse'] != null) ...[
                                const SizedBox(height: 12),
                                Container(
                                  width: double.infinity,
                                  padding: const EdgeInsets.all(12),
                                  decoration: BoxDecoration(
                                    color: Colors.green.shade50,
                                    borderRadius: BorderRadius.circular(10),
                                    border: Border.all(color: Colors.green.shade200),
                                  ),
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      const Row(
                                        children: [
                                          Icon(Icons.reply_rounded, size: 16, color: Colors.green),
                                          SizedBox(width: 6),
                                          Text('Réponse du Superviseur :', style: TextStyle(fontWeight: FontWeight.bold, color: Colors.green, fontSize: 12)),
                                        ],
                                      ),
                                      const SizedBox(height: 4),
                                      Text(r['reponse']!, style: const TextStyle(fontSize: 13, color: Colors.black87)),
                                    ],
                                  ),
                                ),
                              ],
                            ],
                          ),
                        ),
                      );
                    },
                  ),
      ),
      floatingActionButton: FloatingActionButton(
        backgroundColor: const Color(0xFF0369a1),
        foregroundColor: Colors.white,
        onPressed: _ouvrirCreationDialog,
        child: const Icon(Icons.add),
      ),
    );
  }
}
