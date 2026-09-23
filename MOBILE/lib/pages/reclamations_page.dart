import 'package:flutter/material.dart';
import '../core/app_theme.dart';
import '../services/api_service.dart';
import '../services/reclamation_service.dart';

const _types = {
  'PANNE_MECANIQUE': 'Panne mécanique',
  'ROUTE_BLOQUEE': 'Route bloquée',
  'BAC_DEGRADE': 'Bac dégradé / manquant',
  'AUTRE': 'Autre problème',
};

IconData _typeIcon(Object? type) => switch (type) {
      'PANNE_MECANIQUE' => Icons.build_outlined,
      'ROUTE_BLOQUEE' => Icons.wrong_location_outlined,
      'BAC_DEGRADE' => Icons.delete_outline,
      _ => Icons.chat_bubble_outline,
    };

String _date(Object? value) {
  final date = DateTime.tryParse('$value')?.toLocal();
  if (date == null) return 'Date non renseignée';
  String two(int value) => value.toString().padLeft(2, '0');
  return '${two(date.day)}/${two(date.month)}/${date.year} à ${two(date.hour)}:${two(date.minute)}';
}

class ReclamationsPage extends StatefulWidget {
  const ReclamationsPage({super.key});
  @override
  State<ReclamationsPage> createState() => _ReclamationsPageState();
}

class _ReclamationsPageState extends State<ReclamationsPage> {
  List<Map<String, dynamic>> _reclamations = [];
  bool _loading = true;
  bool _creating = false;
  String? _error;
  String _filter = 'ALL';

  @override
  void initState() {
    super.initState();
    _loadReclamations();
  }

  Future<void> _loadReclamations() async {
    if (!mounted) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final result = await ReclamationService.getMyReclamations();
      final list = result
          .whereType<Map>()
          .map((item) => Map<String, dynamic>.from(item))
          .toList();
      list.sort((a, b) =>
          '${b['dateCreation'] ?? ''}'.compareTo('${a['dateCreation'] ?? ''}'));
      if (!mounted) return;
      setState(() => _reclamations = list);
    } catch (error) {
      if (mounted) setState(() => _error = ApiService.errorMessage(error));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _create() async {
    if (_creating) return;
    setState(() => _creating = true);
    final created = await showModalBottomSheet<Map<String, dynamic>>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      enableDrag: false,
      isDismissible: false,
      showDragHandle: false,
      constraints: const BoxConstraints(maxWidth: 640),
      builder: (_) => const _ReclamationForm(),
    );
    if (!mounted) return;
    setState(() {
      _creating = false;
      if (created != null) {
        _filter = 'ALL';
        _reclamations = [created, ..._reclamations];
      }
    });
    if (created != null) {
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Votre réclamation a été envoyée.')));
      await _loadReclamations();
    }
  }

  Widget _filterChip(String value, String title, int count) => ChoiceChip(
        label: Text('$title · $count'),
        selected: _filter == value,
        showCheckmark: false,
        selectedColor: GestiTheme.soft,
        backgroundColor: Colors.white,
        side: BorderSide(
            color: _filter == value ? GestiTheme.primary : GestiTheme.border),
        labelStyle: TextStyle(
            color: _filter == value ? GestiTheme.primaryDark : GestiTheme.muted,
            fontWeight: _filter == value ? FontWeight.w600 : FontWeight.w400),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
        onSelected: (_) => setState(() => _filter = value),
      );

  @override
  Widget build(BuildContext context) {
    final filtered = _reclamations
        .where((r) => _filter == 'ALL' || r['statut'] == _filter)
        .toList();
    return Scaffold(
      appBar: AppBar(
        title: const Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Réclamations'),
              Text('Suivi des signalements',
                  style: TextStyle(fontSize: 12, color: GestiTheme.muted)),
            ]),
        actions: [
          IconButton(
              onPressed: _loading ? null : _loadReclamations,
              tooltip: 'Actualiser les réclamations',
              icon: const Icon(Icons.refresh)),
          const SizedBox(width: 8)
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _loadReclamations,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(20, 28, 20, 24),
          children: [
            Center(
                child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 880),
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text('Mes réclamations',
                        style: Theme.of(context).textTheme.headlineMedium),
                    const SizedBox(height: 10),
                    const Text(
                        'Signalez un problème rencontré sur le terrain et retrouvez les réponses de votre superviseur.',
                        style: TextStyle(color: GestiTheme.muted)),
                    const SizedBox(height: 24),
                    if (_reclamations.isNotEmpty) ...[
                      Wrap(spacing: 8, runSpacing: 8, children: [
                        _filterChip('ALL', 'Toutes', _reclamations.length),
                        _filterChip(
                            'EN_ATTENTE',
                            'En attente',
                            _reclamations
                                .where((r) => r['statut'] == 'EN_ATTENTE')
                                .length),
                        _filterChip(
                            'TRAITEE',
                            'Traitées',
                            _reclamations
                                .where((r) => r['statut'] == 'TRAITEE')
                                .length),
                      ]),
                      const SizedBox(height: 20),
                    ],
                    if (_error != null) ...[
                      Card(
                          child: Padding(
                              padding: const EdgeInsets.all(20),
                              child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    const Icon(Icons.cloud_off_outlined,
                                        color: GestiTheme.amber),
                                    const SizedBox(height: 12),
                                    Text(
                                        'Impossible de charger les réclamations',
                                        style: Theme.of(context)
                                            .textTheme
                                            .titleMedium),
                                    const SizedBox(height: 8),
                                    Text(_error!),
                                    if (_reclamations.isNotEmpty)
                                      const Padding(
                                          padding: EdgeInsets.only(top: 8),
                                          child: Text(
                                              'Les informations déjà chargées restent affichées.',
                                              style: TextStyle(
                                                  color: GestiTheme.muted))),
                                    const SizedBox(height: 8),
                                    TextButton.icon(
                                        onPressed:
                                            _loading ? null : _loadReclamations,
                                        icon: const Icon(Icons.refresh),
                                        label: const Text('Réessayer')),
                                  ]))),
                      const SizedBox(height: 16),
                    ],
                    if (_loading)
                      const Padding(
                          padding: EdgeInsets.symmetric(vertical: 24),
                          child: Center(
                              child: CircularProgressIndicator(
                                  semanticsLabel:
                                      'Chargement des réclamations'))),
                    if (!_loading && _error == null && _reclamations.isEmpty)
                      Card(
                          child: Padding(
                              padding: const EdgeInsets.symmetric(
                                  horizontal: 24, vertical: 36),
                              child: Column(children: [
                                Container(
                                    padding: const EdgeInsets.all(16),
                                    decoration: BoxDecoration(
                                        color: GestiTheme.soft,
                                        borderRadius:
                                            BorderRadius.circular(16)),
                                    child: const Icon(
                                        Icons.mark_chat_unread_outlined,
                                        size: 32,
                                        color: GestiTheme.primaryDark)),
                                const SizedBox(height: 20),
                                Text('Aucune réclamation pour le moment',
                                    textAlign: TextAlign.center,
                                    style:
                                        Theme.of(context).textTheme.titleLarge),
                                const SizedBox(height: 10),
                                const Text(
                                    'Une panne, une route bloquée ou un bac endommagé ? Décrivez le problème avec le bouton ci-dessous.',
                                    textAlign: TextAlign.center,
                                    style: TextStyle(color: GestiTheme.muted)),
                              ]))),
                    if (!_loading &&
                        _reclamations.isNotEmpty &&
                        filtered.isEmpty)
                      const Padding(
                          padding: EdgeInsets.all(24),
                          child: Text(
                              'Aucune réclamation dans cette catégorie.',
                              textAlign: TextAlign.center,
                              style: TextStyle(color: GestiTheme.muted))),
                    for (final item in filtered)
                      Padding(
                          padding: const EdgeInsets.only(bottom: 16),
                          child: _ReclamationCard(
                              key: ValueKey(item['id']), item: item)),
                  ]),
            ))
          ],
        ),
      ),
      bottomNavigationBar: Container(
        decoration: const BoxDecoration(
            color: Colors.white,
            border: Border(top: BorderSide(color: GestiTheme.border))),
        child: SafeArea(
            top: false,
            child: Padding(
                padding: const EdgeInsets.fromLTRB(20, 12, 20, 12),
                child: Center(
                    heightFactor: 1,
                    child: ConstrainedBox(
                        constraints: const BoxConstraints(maxWidth: 880),
                        child: SizedBox(
                            width: double.infinity,
                            child: FilledButton.icon(
                                onPressed: _creating ? null : _create,
                                icon: const Icon(Icons.add_comment_outlined,
                                    size: 20),
                                label: const Text('Nouvelle réclamation'))))))),
      ),
    );
  }
}

class _ReclamationCard extends StatefulWidget {
  final Map<String, dynamic> item;
  const _ReclamationCard({super.key, required this.item});
  @override
  State<_ReclamationCard> createState() => _ReclamationCardState();
}

class _ReclamationCardState extends State<_ReclamationCard> {
  bool _expanded = false;
  @override
  Widget build(BuildContext context) {
    final item = widget.item;
    final treated = item['statut'] == 'TRAITEE';
    final status = treated
        ? 'Traitée'
        : item['statut'] == 'EN_ATTENTE'
            ? 'En attente'
            : 'Statut à confirmer';
    final description = '${item['description'] ?? ''}';
    final canCollapse =
        description.length > 180 || '\n'.allMatches(description).length > 3;
    final response = '${item['reponse'] ?? ''}'.trim();
    return Card(
        child: Padding(
            padding: const EdgeInsets.all(20),
            child:
                Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                        color: GestiTheme.soft,
                        borderRadius: BorderRadius.circular(12)),
                    child: Icon(_typeIcon(item['type']),
                        size: 20, color: GestiTheme.primaryDark)),
                const SizedBox(width: 12),
                Expanded(
                    child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                      Text(_types[item['type']] ?? 'Type non renseigné',
                          style: const TextStyle(
                              fontSize: 12, color: GestiTheme.muted)),
                      const SizedBox(height: 4),
                      Text('${item['titre'] ?? 'Sans titre'}',
                          style: Theme.of(context).textTheme.titleMedium),
                    ])),
              ]),
              const SizedBox(height: 16),
              Wrap(
                  spacing: 12,
                  runSpacing: 8,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 10, vertical: 5),
                        decoration: BoxDecoration(
                            color: treated
                                ? GestiTheme.soft
                                : GestiTheme.amberSoft,
                            borderRadius: BorderRadius.circular(8)),
                        child: Text(status,
                            style: TextStyle(
                                fontSize: 12,
                                fontWeight: FontWeight.w600,
                                color: treated
                                    ? GestiTheme.primaryDark
                                    : GestiTheme.amber))),
                    Text(_date(item['dateCreation']),
                        style: const TextStyle(
                            fontSize: 12, color: GestiTheme.muted)),
                  ]),
              const Padding(
                  padding: EdgeInsets.symmetric(vertical: 16),
                  child: Divider()),
              Text(description,
                  maxLines: _expanded || !canCollapse ? null : 5,
                  overflow:
                      _expanded || !canCollapse ? null : TextOverflow.ellipsis),
              if (canCollapse)
                TextButton(
                    onPressed: () => setState(() => _expanded = !_expanded),
                    child: Text(_expanded
                        ? 'Réduire la description'
                        : 'Lire la description complète')),
              if (response.isNotEmpty) ...[
                const SizedBox(height: 16),
                Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                        color: GestiTheme.soft,
                        borderRadius: BorderRadius.circular(12)),
                    child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Row(children: [
                            Icon(Icons.reply_outlined,
                                size: 18, color: GestiTheme.primaryDark),
                            SizedBox(width: 8),
                            Expanded(
                                child: Text('Réponse du superviseur',
                                    style: TextStyle(
                                        fontSize: 13,
                                        fontWeight: FontWeight.w700,
                                        color: GestiTheme.primaryDark)))
                          ]),
                          const SizedBox(height: 8),
                          Text(response),
                          if (item['dateTraitement'] != null)
                            Padding(
                                padding: const EdgeInsets.only(top: 10),
                                child: Text(_date(item['dateTraitement']),
                                    style: const TextStyle(
                                        fontSize: 12,
                                        color: GestiTheme.muted))),
                        ])),
              ],
            ])));
  }
}

class _ReclamationForm extends StatefulWidget {
  const _ReclamationForm();
  @override
  State<_ReclamationForm> createState() => _ReclamationFormState();
}

class _ReclamationFormState extends State<_ReclamationForm> {
  final _form = GlobalKey<FormState>();
  final _title = TextEditingController();
  final _description = TextEditingController();
  String _type = 'PANNE_MECANIQUE';
  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _title.dispose();
    _description.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_submitting || !_form.currentState!.validate()) return;
    FocusScope.of(context).unfocus();
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      final created = await ReclamationService.createReclamation(
          _title.text.trim(), _type, _description.text.trim());
      if (!mounted) return;
      Navigator.of(context).pop(created);
    } catch (error) {
      if (mounted) setState(() => _error = ApiService.errorMessage(error));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) => PopScope(
        canPop: !_submitting,
        child: Padding(
          padding:
              EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
          child: SafeArea(
              top: false,
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
                child: Form(
                    key: _form,
                    child: Column(
                        mainAxisSize: MainAxisSize.min,
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          Row(children: [
                            Expanded(
                                child: Text('Nouvelle réclamation',
                                    style: Theme.of(context)
                                        .textTheme
                                        .titleLarge)),
                            IconButton(
                                onPressed: _submitting
                                    ? null
                                    : () => Navigator.pop(context),
                                tooltip: 'Fermer le formulaire',
                                icon: const Icon(Icons.close))
                          ]),
                          const SizedBox(height: 8),
                          const Text(
                              'Précisez le lieu et les faits utiles pour aider votre superviseur à comprendre le problème.',
                              style: TextStyle(color: GestiTheme.muted)),
                          const SizedBox(height: 24),
                          Semantics(
                              container: true,
                              explicitChildNodes: true,
                              child: DropdownButtonFormField<String>(
                                  value: _type,
                                  isExpanded: true,
                                  decoration: const InputDecoration(
                                      labelText: 'Type de problème'),
                                  items: _types.entries
                                      .map((entry) => DropdownMenuItem(
                                          value: entry.key,
                                          child: Text(entry.value)))
                                      .toList(),
                                  onChanged: _submitting
                                      ? null
                                      : (value) => setState(
                                          () => _type = value ?? _type))),
                          const SizedBox(height: 20),
                          Semantics(
                              container: true,
                              explicitChildNodes: true,
                              child: TextFormField(
                                  controller: _title,
                                  enabled: !_submitting,
                                  maxLength: 200,
                                  textCapitalization:
                                      TextCapitalization.sentences,
                                  textInputAction: TextInputAction.next,
                                  decoration: const InputDecoration(
                                      labelText: 'Sujet',
                                      hintText:
                                          'Ex. : bac endommagé, rue des Oliviers'),
                                  validator: (value) =>
                                      (value?.trim().isEmpty ?? true)
                                          ? 'Ajoutez un sujet.'
                                          : null)),
                          const SizedBox(height: 12),
                          Semantics(
                              container: true,
                              explicitChildNodes: true,
                              child: TextFormField(
                                  controller: _description,
                                  enabled: !_submitting,
                                  minLines: 4,
                                  maxLines: 7,
                                  maxLength: 5000,
                                  textCapitalization:
                                      TextCapitalization.sentences,
                                  decoration: const InputDecoration(
                                      labelText: 'Description',
                                      alignLabelWithHint: true,
                                      hintText:
                                          'Que s’est-il passé ? Où ? Quel impact sur votre collecte ?'),
                                  validator: (value) =>
                                      (value?.trim().isEmpty ?? true)
                                          ? 'Décrivez le problème rencontré.'
                                          : null)),
                          if (_error != null)
                            Padding(
                                padding:
                                    const EdgeInsets.only(top: 12, bottom: 8),
                                child: Semantics(
                                    liveRegion: true,
                                    child: Container(
                                        padding: const EdgeInsets.all(14),
                                        decoration: BoxDecoration(
                                            color: GestiTheme.amberSoft,
                                            borderRadius:
                                                BorderRadius.circular(12)),
                                        child: Text(
                                            'Envoi non confirmé. ${_error!}',
                                            style: const TextStyle(
                                                color: GestiTheme.amber))))),
                          const SizedBox(height: 20),
                          FilledButton.icon(
                              onPressed: _submitting ? null : _submit,
                              icon: _submitting
                                  ? const SizedBox(
                                      width: 18,
                                      height: 18,
                                      child: CircularProgressIndicator(
                                          strokeWidth: 2))
                                  : const Icon(Icons.send_outlined, size: 18),
                              label: Text(_submitting
                                  ? 'Envoi en cours…'
                                  : 'Envoyer la réclamation')),
                        ])),
              )),
        ),
      );
}
