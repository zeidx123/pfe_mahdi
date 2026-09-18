import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:image_picker/image_picker.dart';
import 'package:latlong2/latlong.dart';
import 'package:url_launcher/url_launcher.dart';
import '../models/tour_progress.dart';
import '../services/api_service.dart';
import '../services/photo_draft_service.dart';
import '../services/tournee_service.dart';
import '../services/tracking_service.dart';
import '../widgets/evidence_image.dart';

class ActiveTrackingPage extends StatefulWidget {
  final Map<String, dynamic> tournee;
  final Future<PhotoDraft?> Function() loadDraft;
  final Future<void> Function() clearDraft;
  const ActiveTrackingPage({
    super.key,
    required this.tournee,
    this.loadDraft = PhotoDraftService.load,
    this.clearDraft = PhotoDraftService.clear,
  });
  @override
  State<ActiveTrackingPage> createState() => _ActiveTrackingPageState();
}

class _ActiveTrackingPageState extends State<ActiveTrackingPage>
    with WidgetsBindingObserver {
  late Map<String, dynamic> _tour;
  bool _busy = false;
  bool _loading = true;
  String? _error;
  String? _analysisError;
  String? _storageWarning;
  String? _driverAssessment;
  PhotoDraft? _draft;
  Timer? _clock;
  TourProgress get _progress => TourProgress(_tour);
  String get _id => _tour['id'].toString();
  bool get _active => _tour['statut'] == 'EN_COURS';

  @override
  void initState() {
    super.initState();
    _tour = Map<String, dynamic>.from(widget.tournee);
    WidgetsBinding.instance.addObserver(this);
    _refresh();
    _clock = Timer.periodic(const Duration(seconds: 10), (_) {
      if (mounted) setState(() {});
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _clock?.cancel();
    // Tracking belongs to the active tour, not this screen; opening Maps/home
    // must not stop it. Completion and logout explicitly stop tracking.
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && !_busy) _refresh();
  }

  void _setTour(Map<String, dynamic> tour) {
    final previous = _progress.currentVisit?['pointId'];
    setState(() {
      _tour = tour;
      if (previous != _progress.currentVisit?['pointId']) {
        _driverAssessment = null;
        _analysisError = null;
      }
    });
  }

  Future<void> _ensureTracking() async {
    if (_active) {
      try {
        await TrackingService.startTracking(
          camionId: '${_tour['camionId'] ?? ''}',
          camionMatricule: '${_tour['camionMatricule'] ?? ''}',
          tourneeId: _id,
          tourneeReference: '${_tour['reference'] ?? ''}',
        );
      } catch (_) {
        TrackingService.state.value = const TrackingState(
            error:
                'Localisation indisponible. Vérifiez le GPS et les permissions puis réessayez.');
      }
    } else if (['TERMINEE', 'ANNULEE'].contains(_tour['statut'])) {
      await TrackingService.stopTracking();
    }
  }

  Future<void> _refresh() async {
    try {
      final data = await TourneeService.getTourneeById(_id);
      final draft = await widget.loadDraft();
      final alreadyStored = draft != null &&
          await TourneeService.draftMatchesStoredPhoto(draft, data);
      if (!mounted) return;
      _setTour(data);
      setState(() {
        _loading = false;
        _error = null;
        _draft = alreadyStored ? null : draft;
        _storageWarning = null;
      });
      if (alreadyStored) await _clearStoredDraft();
      await _ensureTracking();
    } catch (error) {
      if (mounted) {
        setState(() {
          _loading = false;
          _error = ApiService.errorMessage(error);
        });
      }
    }
  }

  Future<void> _run(Future<Map<String, dynamic>> Function() action) async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final data = await action();
      if (!mounted) return;
      _setTour(data);
      await _ensureTracking();
    } catch (error) {
      // A timeout does not prove failure. Reload confirmed progress before retry.
      try {
        final data = await TourneeService.getTourneeById(_id);
        if (mounted) _setTour(data);
      } catch (_) {}
      if (mounted) setState(() => _error = ApiService.errorMessage(error));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _navigate() async {
    final location = TourProgress.coordinate(_progress.destination);
    if (location == null) return;
    try {
      if (!await launchUrl(TourProgress.navigationUri(location),
          mode: kIsWeb
              ? LaunchMode.platformDefault
              : LaunchMode.externalApplication,
          webOnlyWindowName: '_blank')) {
        throw const FormatException('Impossible d’ouvrir Google Maps.');
      }
    } catch (error) {
      if (mounted) setState(() => _error = ApiService.errorMessage(error));
    }
  }

  Future<void> _capture(String stage) async {
    if (_busy) return;
    final pointId = _progress.currentVisit?['pointId']?.toString();
    if (pointId == null) return;
    if (_draft != null) {
      setState(() => _error =
          'Envoyez ou supprimez le brouillon existant avant de reprendre une photo.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final picker = ImagePicker();
      final photo = await picker.pickImage(
          source: kIsWeb ? ImageSource.gallery : ImageSource.camera,
          imageQuality: 80,
          maxWidth: 1600,
          preferredCameraDevice: CameraDevice.rear);
      if (photo == null) return;
      final draft = await PhotoDraftService.save(photo, _id, pointId, stage);
      if (!mounted) return;
      setState(() => _draft = draft);
    } catch (error) {
      if (mounted) {
        setState(() => _error = error is FormatException
            ? error.message
            : kIsWeb
                ? 'Photo non enregistrée. Sélectionnez un fichier JPEG ou PNG puis réessayez.'
                : 'Photo non enregistrée. Vérifiez les permissions caméra puis réessayez.');
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
    if (mounted && _draft?.tourId == _id && _draft?.pointId == pointId) {
      await _uploadDraft();
    }
  }

  Future<void> _uploadDraft() async {
    final draft = _draft;
    if (_busy ||
        draft == null ||
        draft.tourId != _id ||
        draft.pointId != _progress.currentVisit?['pointId']) return;
    setState(() {
      _busy = true;
      _error = null;
      _analysisError = null;
    });
    try {
      final data = await TourneeService.upload(draft);
      if (!mounted) return;
      _setTour(data);
      setState(() => _draft = null);
      await _clearStoredDraft();
      if (draft.stage == 'AFTER') {
        try {
          final analysed = await TourneeService.analyse(_id, draft.pointId);
          if (mounted) _setTour(analysed);
        } catch (_) {
          if (mounted) {
            setState(() => _analysisError =
                'Photo enregistrée. Analyse indisponible : vous pouvez réessayer ou continuer avec votre observation.');
          }
        }
      }
    } catch (error) {
      if (mounted) {
        setState(() => _error =
            '${ApiService.errorMessage(error)} La photo reste en brouillon.');
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _clearStoredDraft() async {
    try {
      await widget.clearDraft();
      if (mounted) setState(() => _storageWarning = null);
    } catch (_) {
      if (mounted) {
        setState(() => _storageWarning =
            'Photo enregistrée sur le serveur. La copie locale n’a pas pu être supprimée ; vous pouvez continuer. Actualisez pour réessayer le nettoyage local.');
      }
    }
  }

  Future<void> _discardDraft() async {
    final confirmed = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
              title: const Text('Supprimer le brouillon ?'),
              content: const Text(
                  'Cette photo locale non envoyée sera supprimée. Les photos déjà enregistrées restent disponibles.'),
              actions: [
                TextButton(
                    onPressed: () => Navigator.pop(ctx, false),
                    child: const Text('Conserver')),
                TextButton(
                    onPressed: () => Navigator.pop(ctx, true),
                    child: const Text('Supprimer'))
              ],
            ));
    if (confirmed != true || !mounted) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await widget.clearDraft();
      if (mounted) setState(() => _draft = null);
    } catch (_) {
      if (mounted) {
        setState(() => _error =
            'Suppression locale impossible. Le brouillon est conservé ; vous pouvez réessayer son envoi ou sa suppression.');
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _complete({required bool issue}) async {
    final visit = _progress.currentVisit;
    if (visit == null) return;
    final controller = TextEditingController();
    final form = GlobalKey<FormState>();
    final confirmed = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
              title: Text(
                  issue ? 'Signaler un problème' : 'Confirmer la collecte'),
              content: Form(
                  key: form,
                  child: Column(mainAxisSize: MainAxisSize.min, children: [
                    Text(issue
                        ? 'Décrivez l’obstruction ou le problème. Si une photo est dangereuse ou impossible, précisez pourquoi.'
                        : 'Vous confirmez avoir effectué la collecte. L’analyse IA reste une aide et le superviseur peut la corriger.'),
                    const SizedBox(height: 12),
                    TextFormField(
                        controller: controller,
                        minLines: 2,
                        maxLines: 4,
                        decoration: InputDecoration(
                            labelText: issue
                                ? 'Motif obligatoire'
                                : 'Note facultative'),
                        validator: (v) =>
                            issue && (v == null || v.trim().isEmpty)
                                ? 'Décrivez le problème'
                                : null),
                  ])),
              actions: [
                TextButton(
                    onPressed: () => Navigator.pop(ctx, false),
                    child: const Text('Annuler')),
                FilledButton(
                    onPressed: () {
                      if (form.currentState!.validate()) {
                        Navigator.pop(ctx, true);
                      }
                    },
                    child: const Text('Enregistrer'))
              ],
            ));
    final notes = controller.text.trim();
    // Wait until the dialog has disposed its TextField before disposing its controller.
    if (!mounted || confirmed != true) return;
    await _run(() => TourneeService.complete(_id, visit['pointId'].toString(),
        outcome: issue ? 'ISSUE' : 'COMPLETED',
        notes: notes,
        driverAssessment: _driverAssessment));
  }

  Future<void> _finish() async {
    final returned = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
              title: const Text('Retour au dépôt'),
              content: const Text(
                  'Confirmez que vous êtes revenu au dépôt. Cette action enregistre la fin de la tournée.'),
              actions: [
                TextButton(
                    onPressed: () => Navigator.pop(ctx, false),
                    child: const Text('Pas encore')),
                FilledButton(
                    onPressed: () => Navigator.pop(ctx, true),
                    child: const Text('Je suis au dépôt'))
              ],
            ));
    if (returned == true && mounted) {
      await _run(() => TourneeService.terminerTournee(_id));
    }
  }

  Widget _card(List<Widget> children) => Card(
      margin: const EdgeInsets.only(bottom: 14),
      child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: children)));

  Widget _message(String text, {bool error = false}) => Container(
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
            color: error ? Colors.red.shade50 : Colors.amber.shade50,
            borderRadius: BorderRadius.circular(10)),
        child: Text(text,
            style: TextStyle(
                color: error ? Colors.red.shade900 : Colors.brown.shade900)),
      );

  Widget _map() {
    final route = _progress.geometry;
    final markers = <Marker>[];
    final depot = TourProgress.coordinate(_progress.route?['depot'] is Map
        ? Map<String, dynamic>.from(_progress.route!['depot'])
        : null);
    if (depot != null) {
      markers.add(Marker(
          point: depot,
          child: const Icon(Icons.warehouse, color: Colors.indigo, size: 30)));
    }
    for (final visit in _progress.visits) {
      final point =
          TourProgress.coordinate(_progress.point(visit['pointId'].toString()));
      if (point != null) {
        markers.add(Marker(
            point: point,
            child: CircleAvatar(
                radius: 14,
                backgroundColor: TourProgress.isHandled(visit)
                    ? Colors.green
                    : const Color(0xFF0369a1),
                child: Text('${(visit['order'] as num? ?? 0) + 1}',
                    style:
                        const TextStyle(color: Colors.white, fontSize: 12)))));
      }
    }
    final center = route.isNotEmpty
        ? route.first
        : depot ?? TourProgress.coordinate(_progress.destination);
    if (center == null) {
      return _message('Carte indisponible : coordonnées manquantes.');
    }
    final coords =
        route.isNotEmpty ? route : markers.map((m) => m.point).toList();
    return SizedBox(
        height: 230,
        child: ClipRRect(
            borderRadius: BorderRadius.circular(12),
            child: FlutterMap(
              key: ValueKey('${_id}_${route.length}'),
              options: MapOptions(
                  initialCenter: center,
                  initialZoom: 13,
                  initialCameraFit: coords.length > 1
                      ? CameraFit.bounds(
                          bounds: LatLngBounds.fromPoints(coords),
                          padding: const EdgeInsets.all(28),
                          maxZoom: 16)
                      : null),
              children: [
                TileLayer(
                    urlTemplate:
                        'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                    userAgentPackageName:
                        'com.municipalite.gesticollecte_mobile'),
                if (route.isNotEmpty)
                  PolylineLayer(polylines: [
                    Polyline(
                        points: route,
                        color: const Color(0xFF0369a1),
                        strokeWidth: 4)
                  ]),
                MarkerLayer(markers: markers),
                ValueListenableBuilder<TrackingState>(
                    valueListenable: TrackingService.state,
                    builder: (context, state, _) => MarkerLayer(
                        markers: state.position == null
                            ? []
                            : [
                                Marker(
                                    point: LatLng(state.position!.latitude,
                                        state.position!.longitude),
                                    child: const Icon(Icons.my_location,
                                        color: Colors.deepOrange, size: 28))
                              ])),
                const RichAttributionWidget(attributions: [
                  TextSourceAttribution('OpenStreetMap contributors')
                ]),
              ],
            )));
  }

  Widget _tracking() => ValueListenableBuilder<TrackingState>(
      valueListenable: TrackingService.state,
      builder: (context, state, _) => _card([
            Row(children: [
              Icon(
                  state.error == null && !state.stale
                      ? Icons.gps_fixed
                      : Icons.gps_not_fixed,
                  color: state.error == null && !state.stale
                      ? Colors.green
                      : Colors.orange),
              const SizedBox(width: 8),
              const Expanded(
                  child: Text('Partage de position',
                      style: TextStyle(fontWeight: FontWeight.bold))),
              if (!state.active || state.error != null)
                TextButton(
                    onPressed: _busy
                        ? null
                        : () async {
                            await TrackingService.stopTracking();
                            await _ensureTracking();
                          },
                    child: const Text('Réessayer'))
            ]),
            Text(state.error ??
                (state.sentAt == null
                    ? 'En attente d’une position GPS…'
                    : state.stale
                        ? 'Position ancienne : le superviseur ne reçoit pas de mise à jour récente.'
                        : 'Dernier envoi : ${DateTime.now().difference(state.sentAt!).inSeconds} s')),
            const SizedBox(height: 4),
            const Text(
                kIsWeb
                    ? 'Gardez cet onglet ouvert. Le navigateur peut suspendre la localisation en arrière-plan ; le suivi Android doit être vérifié sur téléphone.'
                    : 'Le suivi continue à l’ouverture de Maps. Une fermeture forcée peut l’interrompre ; reprenez la tournée ensuite.',
                style: TextStyle(fontSize: 12, color: Colors.black54)),
          ]));

  Widget _currentStop(Map<String, dynamic> visit) {
    final point = _progress.point(visit['pointId'].toString());
    final arrived = visit['status'] == 'ARRIVED';
    final after = visit['afterPhoto'] as String?;
    final before = visit['beforePhoto'] as String?;
    return _card([
      Text(
          'Prochain point · ${point['reference'] ?? point['nom'] ?? visit['pointId']}',
          style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
      if (point['adresse'] != null) Text('${point['adresse']}'),
      const SizedBox(height: 12),
      OutlinedButton.icon(
          onPressed: _busy || TourProgress.coordinate(point) == null
              ? null
              : _navigate,
          icon: const Icon(Icons.navigation),
          label: const Text('Naviguer avec Google Maps')),
      const Text(
          'Maps peut choisir un autre trajet routier. L’ordre des points reste celui de la tournée.',
          style: TextStyle(fontSize: 12, color: Colors.black54)),
      const SizedBox(height: 12),
      if (!arrived)
        FilledButton.icon(
            onPressed: _busy
                ? null
                : () => _run(() =>
                    TourneeService.arrive(_id, visit['pointId'].toString())),
            icon: const Icon(Icons.location_on),
            label: const Text('Je suis arrivé')),
      if (arrived) ...[
        const Text(
            kIsWeb
                ? 'Collectez les déchets, puis sélectionnez une photo APRÈS (JPEG ou PNG).'
                : 'Collectez les déchets, puis prenez une photo APRÈS.',
            style: TextStyle(fontWeight: FontWeight.w600)),
        if (before != null) ...[
          const SizedBox(height: 10),
          EvidenceImage(path: before, label: 'AVANT (facultative)')
        ],
        if (after != null) ...[
          const SizedBox(height: 10),
          EvidenceImage(path: after, label: 'APRÈS enregistrée')
        ],
        const SizedBox(height: 8),
        FilledButton.icon(
            onPressed: _busy || _draft != null ? null : () => _capture('AFTER'),
            icon: const Icon(kIsWeb ? Icons.upload_file : Icons.camera_alt),
            label: Text(kIsWeb
                ? after == null
                    ? 'Choisir la photo APRÈS'
                    : 'Remplacer la photo APRÈS'
                : after == null
                    ? 'Prendre la photo APRÈS'
                    : 'Reprendre la photo APRÈS')),
        TextButton.icon(
            onPressed: _busy || _draft != null || after != null
                ? null
                : () => _capture('BEFORE'),
            icon: const Icon(Icons.add_a_photo_outlined),
            label: const Text('Photo AVANT facultative')),
        if (after != null) ...[
          const Divider(),
          Text(TourProgress.assessmentLabel(visit['analysisStatus']),
              style: const TextStyle(fontWeight: FontWeight.w700)),
          if (_analysisError != null) _message(_analysisError!),
          const Text(
              'L’IA évalue uniquement ce qui est visible. Vous et le superviseur pouvez corriger cette observation.',
              style: TextStyle(fontSize: 12, color: Colors.black54)),
          TextButton.icon(
              onPressed: _busy
                  ? null
                  : () async {
                      setState(() => _analysisError = null);
                      await _run(() => TourneeService.analyse(
                          _id, visit['pointId'].toString()));
                    },
              icon: const Icon(Icons.refresh),
              label: const Text('Relancer l’analyse')),
          DropdownButtonFormField<String>(
              value: _driverAssessment,
              isExpanded: true,
              decoration: const InputDecoration(
                  labelText: 'Mon observation (facultative)'),
              items: TourProgress.assessmentLabels.entries
                  .where((e) => e.key != 'ANALYSIS_UNAVAILABLE')
                  .map((e) => DropdownMenuItem(
                      value: e.key,
                      child: Text(e.value, overflow: TextOverflow.ellipsis)))
                  .toList(),
              onChanged:
                  _busy ? null : (v) => setState(() => _driverAssessment = v)),
          const SizedBox(height: 12),
          FilledButton.icon(
              onPressed: _busy || _draft != null
                  ? null
                  : () => _complete(issue: false),
              icon: const Icon(Icons.check_circle_outline),
              label: const Text('Confirmer la collecte et continuer')),
        ],
      ],
      OutlinedButton.icon(
          onPressed:
              _busy || _draft != null ? null : () => _complete(issue: true),
          icon: const Icon(Icons.report_problem_outlined),
          label: const Text('Signaler un problème et continuer')),
    ]);
  }

  @override
  Widget build(BuildContext context) {
    final visits = _progress.visits;
    final done = visits.where(TourProgress.isHandled).length;
    final current = _progress.currentVisit;
    final start = DateTime.tryParse(
        '${_tour['heureDebutReelle'] ?? _tour['startedAt'] ?? ''}');
    final end = DateTime.tryParse(
        '${_tour['heureFinReelle'] ?? _tour['finishedAt'] ?? ''}');
    final elapsed = start == null
        ? null
        : (end ?? DateTime.now()).difference(start).inSeconds;
    return Scaffold(
      backgroundColor: const Color(0xFFF1F5F9),
      appBar: AppBar(
          title: Text('${_tour['reference'] ?? 'Tournée'}'),
          backgroundColor: const Color(0xFF0369a1),
          foregroundColor: Colors.white,
          actions: [
            IconButton(
                onPressed: _busy ? null : _refresh,
                tooltip: 'Actualiser',
                icon: const Icon(Icons.refresh)),
            IconButton(
                onPressed: () => Navigator.pushNamed(context, '/assistant'),
                tooltip: 'Assistant',
                icon: const Icon(Icons.smart_toy_outlined))
          ]),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: _refresh,
              child: ListView(padding: const EdgeInsets.all(16), children: [
                if (_busy) const LinearProgressIndicator(),
                if (_busy)
                  const Padding(
                      padding: EdgeInsets.symmetric(vertical: 8),
                      child: Text('Enregistrement / analyse en cours…')),
                if (_error != null) _message(_error!, error: true),
                if (_storageWarning != null) _message(_storageWarning!),
                _card([
                  Text('${_tour['nom'] ?? 'Tournée de collecte'}',
                      style: const TextStyle(
                          fontSize: 20, fontWeight: FontWeight.bold)),
                  Text(
                      '${_tour['camionMatricule'] ?? 'Camion non renseigné'} · ${_tour['statut'] ?? ''}'),
                  const SizedBox(height: 10),
                  Text('$done / ${visits.length} points traités'),
                  const SizedBox(height: 6),
                  LinearProgressIndicator(
                      value: visits.isEmpty ? 0 : done / visits.length),
                  const SizedBox(height: 10),
                  Text(
                      'Durée prévue : ${TourProgress.duration(_tour['estimatedAtStartSeconds'] ?? _progress.route?['totalSeconds'])}'),
                  if (elapsed != null)
                    Text(
                        'Durée écoulée : ${TourProgress.duration(_tour['actualDurationSeconds'] ?? elapsed)}'),
                  if (_active)
                    Text(
                        'Parcours restant estimé : ${TourProgress.duration(_progress.remainingTravelSeconds)} de trajet + '
                        '${TourProgress.duration(_progress.remainingServiceSeconds)} de service'),
                ]),
                if (_tour['routeError'] != null)
                  _message('${_tour['routeError']}'),
                _map(),
                const SizedBox(height: 14),
                if (_draft != null)
                  _card([
                    const Text('Photo en brouillon',
                        style: TextStyle(fontWeight: FontWeight.bold)),
                    Text(_draft!.tourId == _id &&
                            _draft!.pointId == current?['pointId']
                        ? 'Photo ${_draft!.stage == 'AFTER' ? 'APRÈS' : 'AVANT'} conservée sur cet appareil, en attente d’envoi.'
                        : 'Une photo non envoyée d’un autre point ou d’une autre tournée est conservée sur cet appareil.'),
                    if (_draft!.tourId == _id &&
                        _draft!.pointId == current?['pointId'])
                      FilledButton(
                          onPressed: _busy ? null : _uploadDraft,
                          child: const Text('Réessayer l’envoi')),
                    TextButton(
                        onPressed: _busy ? null : _discardDraft,
                        child: const Text('Supprimer le brouillon')),
                  ]),
                if (_tour['statut'] == 'PLANIFIEE')
                  _card([
                    if (_progress.route == null)
                      _message(
                          'Le superviseur doit calculer et enregistrer le parcours routier avant le départ.'),
                    FilledButton.icon(
                        onPressed: _busy || _progress.route == null
                            ? null
                            : () =>
                                _run(() => TourneeService.demarrerTournee(_id)),
                        icon: const Icon(Icons.play_arrow),
                        label: const Text('Démarrer la tournée')),
                  ]),
                if (_active) _tracking(),
                if (_active && current != null) _currentStop(current),
                if (_active && visits.isEmpty)
                  _message(
                      'Aucun suivi de point disponible pour cette tournée historique. Contactez le superviseur.'),
                if (_active && _progress.allHandled)
                  _card([
                    const Text('Tous les points sont traités. Retour au dépôt.',
                        style: TextStyle(
                            fontSize: 18, fontWeight: FontWeight.bold)),
                    Text(
                        '${_progress.route?['depot']?['label'] ?? 'Dépôt configuré'}'),
                    const SizedBox(height: 10),
                    OutlinedButton.icon(
                        onPressed: _busy ||
                                TourProgress.coordinate(
                                        _progress.destination) ==
                                    null
                            ? null
                            : _navigate,
                        icon: const Icon(Icons.navigation),
                        label: const Text('Naviguer vers le dépôt')),
                    FilledButton.icon(
                        onPressed: _busy ? null : _finish,
                        icon: const Icon(Icons.flag),
                        label: const Text('Confirmer le retour et terminer')),
                  ]),
                if (_tour['statut'] == 'TERMINEE')
                  _message(
                      'Tournée terminée. Le superviseur peut consulter les observations et les preuves.'),
                _card([
                  const Text('Points de la tournée',
                      style:
                          TextStyle(fontSize: 17, fontWeight: FontWeight.bold)),
                  for (final visit in visits)
                    ExpansionTile(
                        tilePadding: EdgeInsets.zero,
                        leading: Icon(
                            visit['status'] == 'ISSUE_REPORTED'
                                ? Icons.warning_amber
                                : TourProgress.isHandled(visit)
                                    ? Icons.check_circle
                                    : Icons.location_on_outlined,
                            color: visit['status'] == 'ISSUE_REPORTED'
                                ? Colors.orange
                                : TourProgress.isHandled(visit)
                                    ? Colors.green
                                    : Colors.blue),
                        title: Text(
                            '${_progress.point(visit['pointId'].toString())['reference'] ?? visit['pointId']}'),
                        subtitle: Text(visit['status'] == 'ISSUE_REPORTED'
                            ? 'Problème signalé'
                            : TourProgress.isHandled(visit)
                                ? 'Collecte déclarée'
                                : visit['status'] == 'ARRIVED'
                                    ? 'Arrivé'
                                    : 'À visiter'),
                        children: [
                          if (visit['afterPhoto'] != null)
                            EvidenceImage(
                                path: visit['afterPhoto'], label: 'APRÈS'),
                          if (visit['beforePhoto'] != null)
                            EvidenceImage(
                                path: visit['beforePhoto'], label: 'AVANT'),
                          ListTile(
                              contentPadding: EdgeInsets.zero,
                              title: Text(TourProgress.assessmentLabel(
                                  visit['analysisStatus'])),
                              subtitle: Text('${visit['notes'] ?? ''}')),
                          if (visit['reviewLabel'] != null)
                            Text(
                                'Avis superviseur : ${TourProgress.assessmentLabel(visit['reviewLabel'])}'),
                          if (visit['reviewNotes'] != null)
                            Text('${visit['reviewNotes']}'),
                        ]),
                ]),
              ]),
            ),
    );
  }
}
