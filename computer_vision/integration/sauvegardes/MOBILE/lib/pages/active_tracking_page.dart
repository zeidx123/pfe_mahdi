import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:geolocator/geolocator.dart';
import 'package:image_picker/image_picker.dart';
import 'package:latlong2/latlong.dart';
import 'package:url_launcher/url_launcher.dart';
import '../core/app_theme.dart';
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
  bool _dialogOpen = false;
  bool _loading = true;
  bool _automaticAnalysis =
      const bool.fromEnvironment('AUTO_VISION_ANALYSIS', defaultValue: false);
  String? _error;
  String? _analysisError;
  String? _storageWarning;
  String? _driverAssessment;
  PhotoDraft? _draft;
  Timer? _clock;
  final MapController _mapController = MapController();
  bool _autoCentered = false;
  TourProgress get _progress => TourProgress(_tour);
  String get _id => _tour['id'].toString();
  bool get _active => _tour['statut'] == 'EN_COURS';
  bool _hasSavedAnalysis(Map<String, dynamic>? visit) => const {
        'CLEANED',
        'NOT_CLEANED',
        'OBSTRUCTED',
        'CANNOT_DETERMINE',
      }.contains(visit?['analysisStatus']);

  @override
  void initState() {
    super.initState();
    _tour = Map<String, dynamic>.from(widget.tournee);
    WidgetsBinding.instance.addObserver(this);
    TrackingService.state.addListener(_onTrackingState);
    _refresh();
    _clock = Timer.periodic(const Duration(seconds: 10), (_) {
      if (mounted) setState(() {});
    });
  }

  void _onTrackingState() {
    final pos = TrackingService.state.value.position;
    if (pos != null && !_autoCentered && mounted) {
      _autoCentered = true;
      try {
        _mapController.move(LatLng(pos.latitude, pos.longitude), 15);
      } catch (_) {}
    }
  }

  Future<void> _centerOnMyLocation() async {
    final current = TrackingService.state.value.position;
    if (current != null) {
      _mapController.move(LatLng(current.latitude, current.longitude), 15);
      return;
    }
    try {
      final allowed = await TrackingService.requestPermission();
      if (!allowed) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
                content: Text('Veuillez activer et autoriser la géolocalisation.')),
          );
        }
        return;
      }
      final pos = await Geolocator.getCurrentPosition();
      if (mounted) {
        _mapController.move(LatLng(pos.latitude, pos.longitude), 15);
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Position GPS introuvable.')),
        );
      }
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    TrackingService.state.removeListener(_onTrackingState);
    _clock?.cancel();
    _mapController.dispose();
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
    if (_busy || _dialogOpen) return;
    setState(() => _busy = true);
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
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _run(Future<Map<String, dynamic>> Function() action) async {
    if (_busy || _dialogOpen) return;
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
    if (_busy || _dialogOpen) return;
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
        _dialogOpen ||
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
      if (mounted &&
          draft.stage == 'AFTER' &&
          _automaticAnalysis &&
          !_hasSavedAnalysis(_progress.currentVisit)) {
        try {
          final analysed = await TourneeService.analyse(_id, draft.pointId);
          if (mounted) _setTour(analysed);
        } catch (_) {
          // A timed-out response may already have been saved on the server.
          try {
            final confirmed = await TourneeService.getTourneeById(_id);
            if (mounted) _setTour(confirmed);
          } catch (_) {}
          if (mounted) {
            setState(() => _analysisError = _hasSavedAnalysis(
                    _progress.currentVisit)
                ? null
                : 'Photo enregistrée. Analyse indisponible : vous pouvez réessayer ou continuer avec votre observation.');
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

  Future<void> _analyseVisit(String pointId) async {
    if (_busy ||
        _dialogOpen ||
        _progress.currentVisit?['pointId'] != pointId ||
        _hasSavedAnalysis(_progress.currentVisit)) return;
    setState(() => _analysisError = null);
    await _run(() => TourneeService.analyse(_id, pointId));
  }

  Future<bool?> _confirmAction(WidgetBuilder builder) async {
    if (_busy || _dialogOpen) return false;
    setState(() => _dialogOpen = true);
    try {
      return await showDialog<bool>(context: context, builder: builder);
    } finally {
      if (mounted) setState(() => _dialogOpen = false);
    }
  }

  Future<void> _discardDraft() async {
    if (_busy || _dialogOpen) return;
    final confirmed = await _confirmAction((ctx) => AlertDialog(
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
    if (_busy || _dialogOpen) return;
    final visit = _progress.currentVisit;
    if (visit == null) return;
    final controller = TextEditingController();
    final form = GlobalKey<FormState>();
    final confirmed = await _confirmAction((ctx) => AlertDialog(
          title: Text(issue ? 'Signaler un problème' : 'Confirmer la collecte'),
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
                        labelText:
                            issue ? 'Motif obligatoire' : 'Note facultative'),
                    validator: (v) => issue && (v == null || v.trim().isEmpty)
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
    if (_busy || _dialogOpen) return;
    final returned = await _confirmAction((ctx) => AlertDialog(
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

  Widget _card(List<Widget> children, {bool highlighted = false}) => Container(
        margin: const EdgeInsets.only(bottom: 16),
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
              color: highlighted
                  ? GestiTheme.primary.withOpacity(.35)
                  : GestiTheme.border),
          boxShadow: const [
            BoxShadow(
                color: Color(0x0415222E), blurRadius: 12, offset: Offset(0, 3))
          ],
        ),
        child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch, children: children),
      );

  Widget _heading(String title, {String? subtitle, IconData? icon}) => Padding(
        padding: const EdgeInsets.only(bottom: 16),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          if (icon != null) ...[
            Container(
                padding: const EdgeInsets.all(9),
                decoration: BoxDecoration(
                    color: GestiTheme.soft,
                    borderRadius: BorderRadius.circular(10)),
                child: Icon(icon, size: 20, color: GestiTheme.primaryDark)),
            const SizedBox(width: 12),
          ],
          Expanded(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                Text(title,
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        color: GestiTheme.ink, fontWeight: FontWeight.w700)),
                if (subtitle != null) ...[
                  const SizedBox(height: 4),
                  Text(subtitle,
                      style: const TextStyle(
                          color: GestiTheme.muted, fontSize: 13, height: 1.5)),
                ],
              ])),
        ]),
      );

  Widget _badge(String label, {Color color = GestiTheme.primaryDark}) =>
      Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        decoration: BoxDecoration(
            color: color.withOpacity(.08),
            borderRadius: BorderRadius.circular(8)),
        child: Text(label,
            style: TextStyle(
                color: color, fontSize: 12, fontWeight: FontWeight.w600)),
      );

  Widget _message(String text, {bool error = false}) => Semantics(
        liveRegion: true,
        child: Container(
          margin: const EdgeInsets.only(bottom: 12),
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
              color: error ? const Color(0xFFFFF2F0) : const Color(0xFFFFF8E9),
              border: Border.all(
                  color: error
                      ? const Color(0xFFF3D1CC)
                      : const Color(0xFFF0E2BF)),
              borderRadius: BorderRadius.circular(12)),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Icon(error ? Icons.error_outline : Icons.info_outline,
                color:
                    error ? const Color(0xFFA4352A) : const Color(0xFF8D651C),
                size: 20),
            const SizedBox(width: 10),
            Expanded(
                child: Text(text,
                    style: TextStyle(
                        height: 1.5,
                        fontSize: 13,
                        color: error
                            ? const Color(0xFFA4352A)
                            : const Color(0xFF7C5D25)))),
          ]),
        ),
      );

  Widget _metric(String label, String value, IconData icon) => Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
            color: GestiTheme.bg, borderRadius: BorderRadius.circular(12)),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Icon(icon, size: 19, color: GestiTheme.muted),
          const SizedBox(width: 10),
          Expanded(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                Text(label,
                    style:
                        const TextStyle(fontSize: 12, color: GestiTheme.muted)),
                const SizedBox(height: 5),
                Text(value,
                    style: const TextStyle(
                        fontSize: 17,
                        fontWeight: FontWeight.w700,
                        color: GestiTheme.ink)),
              ])),
        ]),
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
          child: Container(
              decoration: BoxDecoration(
                  color: GestiTheme.navy,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: Colors.white, width: 2)),
              child: const Icon(Icons.warehouse_outlined,
                  color: Colors.white, size: 22))));
    }
    for (final visit in _progress.visits) {
      final point =
          TourProgress.coordinate(_progress.point(visit['pointId'].toString()));
      if (point != null) {
        markers.add(Marker(
            point: point,
            child: Container(
                alignment: Alignment.center,
                decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: TourProgress.isHandled(visit)
                        ? GestiTheme.primary
                        : GestiTheme.navy,
                    border: Border.all(color: Colors.white, width: 2)),
                child: Text('${(visit['order'] as num? ?? 0) + 1}',
                    style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.w700,
                        fontSize: 12)))));
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
        height: 280,
        child: ClipRRect(
            borderRadius: BorderRadius.circular(12),
            child: Stack(
              children: [
                FlutterMap(
                  mapController: _mapController,
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
                            color: GestiTheme.primary,
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
                ),
                Positioned(
                  bottom: 12,
                  right: 12,
                  child: Material(
                    color: Colors.white,
                    shape: const CircleBorder(),
                    elevation: 3,
                    shadowColor: Colors.black38,
                    child: InkWell(
                      customBorder: const CircleBorder(),
                      onTap: _centerOnMyLocation,
                      child: const Padding(
                        padding: EdgeInsets.all(8.0),
                        child: Icon(Icons.my_location_rounded,
                            size: 22, color: GestiTheme.primaryDark),
                      ),
                    ),
                  ),
                ),
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
                      ? GestiTheme.primary
                      : const Color(0xFFA77822),
                  size: 20),
              const SizedBox(width: 10),
              const Expanded(
                  child: Text('Partage de position',
                      style: TextStyle(
                          color: GestiTheme.ink,
                          fontSize: 15,
                          fontWeight: FontWeight.w700))),
              if (!state.active || state.error != null)
                TextButton(
                    onPressed: _busy
                        ? null
                        : () async {
                            await TrackingService.stopTracking();
                            await _ensureTracking();
                          },
                    style:
                        TextButton.styleFrom(minimumSize: const Size(48, 48)),
                    child: const Text('Réessayer'))
            ]),
            const SizedBox(height: 10),
            Text(
                state.error ??
                    (state.sentAt == null
                        ? 'En attente d’une position GPS…'
                        : state.stale
                            ? 'Position ancienne : le superviseur ne reçoit pas de mise à jour récente.'
                            : 'Dernier envoi : ${DateTime.now().difference(state.sentAt!).inSeconds} s'),
                style: const TextStyle(
                    color: GestiTheme.ink, fontSize: 13, height: 1.5)),
            const SizedBox(height: 10),
            const Text(
                kIsWeb
                    ? 'Gardez cet onglet ouvert. Le navigateur peut suspendre la localisation en arrière-plan ; le suivi Android doit être vérifié sur téléphone.'
                    : 'Le suivi continue à l’ouverture de Maps. Une fermeture forcée peut l’interrompre ; reprenez la tournée ensuite.',
                style: TextStyle(
                    fontSize: 12, color: GestiTheme.muted, height: 1.5)),
          ]));

  Widget _step(int number, String label,
          {required bool done, required bool active}) =>
      Expanded(
        child: Container(
          margin: const EdgeInsets.symmetric(horizontal: 3),
          padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 12),
          decoration: BoxDecoration(
              color: active ? GestiTheme.soft : GestiTheme.bg,
              borderRadius: BorderRadius.circular(10)),
          child: Column(children: [
            Icon(done ? Icons.check_circle : Icons.radio_button_checked,
                size: 20,
                color: done || active ? GestiTheme.primary : GestiTheme.muted),
            const SizedBox(height: 6),
            Text('$number. $label',
                textAlign: TextAlign.center,
                style: TextStyle(
                    fontSize: 11,
                    fontWeight: active ? FontWeight.w700 : FontWeight.w500,
                    color: active ? GestiTheme.primaryDark : GestiTheme.muted)),
          ]),
        ),
      );

  Widget _evidencePhotos(Map<String, dynamic> visit, {bool current = false}) {
    final after = visit['afterPhoto'] as String?;
    final before = visit['beforePhoto'] as String?;
    final photos = [
      if (after != null)
        EvidenceImage(
            path: after, label: current ? 'APRÈS enregistrée' : 'APRÈS'),
      if (before != null)
        EvidenceImage(
            path: before, label: current ? 'AVANT (facultative)' : 'AVANT'),
    ];
    return LayoutBuilder(builder: (context, constraints) {
      if (photos.length == 2 && constraints.maxWidth >= 460) {
        return Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Expanded(child: photos[0]),
          const SizedBox(width: 12),
          Expanded(child: photos[1]),
        ]);
      }
      return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        for (var i = 0; i < photos.length; i++) ...[
          if (i > 0) const SizedBox(height: 12),
          photos[i],
        ]
      ]);
    });
  }

  Color _assessmentColor(dynamic assessment) {
    if (assessment == 'CLEANED') return GestiTheme.primaryDark;
    if (assessment == 'NOT_CLEANED') return const Color(0xFFA4352A);
    return const Color(0xFF8D651C);
  }

  Widget _currentStop(Map<String, dynamic> visit) {
    final point = _progress.point(visit['pointId'].toString());
    final arrived = visit['status'] == 'ARRIVED';
    final after = visit['afterPhoto'] as String?;
    final before = visit['beforePhoto'] as String?;
    final order = (visit['order'] as num? ?? 0).toInt() + 1;
    return _card([
      Row(children: [
        _badge(arrived ? 'Sur place' : 'Prochaine destination'),
        const Spacer(),
        Text('Point $order / ${_progress.visits.length}',
            style: const TextStyle(
                color: GestiTheme.muted,
                fontSize: 12,
                fontWeight: FontWeight.w600)),
      ]),
      const SizedBox(height: 18),
      Text('${point['reference'] ?? point['nom'] ?? visit['pointId']}',
          style: Theme.of(context)
              .textTheme
              .headlineSmall
              ?.copyWith(fontWeight: FontWeight.w700, color: GestiTheme.ink)),
      if (point['adresse'] != null) ...[
        const SizedBox(height: 6),
        Text('${point['adresse']}',
            style: const TextStyle(color: GestiTheme.muted, height: 1.5)),
      ],
      const SizedBox(height: 18),
      OutlinedButton.icon(
          style: OutlinedButton.styleFrom(minimumSize: const Size(0, 48)),
          onPressed: _busy || TourProgress.coordinate(point) == null
              ? null
              : _navigate,
          icon: const Icon(Icons.near_me_outlined, size: 20),
          label: const Text('Naviguer avec Google Maps',
              textAlign: TextAlign.center)),
      const SizedBox(height: 8),
      const Text(
          'Le guidage s’ouvre dans Maps. Conservez l’ordre des points prévu dans la tournée.',
          style: TextStyle(fontSize: 12, color: GestiTheme.muted, height: 1.5)),
      const SizedBox(height: 20),
      Row(children: [
        _step(1, 'Arrivée', done: arrived, active: !arrived),
        _step(2, 'Photo APRÈS',
            done: after != null, active: arrived && after == null),
        _step(3, 'Validation', done: false, active: arrived && after != null),
      ]),
      const SizedBox(height: 20),
      if (!arrived) ...[
        const Text('En route vers le point',
            style:
                TextStyle(color: GestiTheme.ink, fontWeight: FontWeight.w600)),
        const SizedBox(height: 6),
        const Text(
            'À destination, confirmez votre arrivée pour enregistrer la collecte et sa photo.',
            style:
                TextStyle(color: GestiTheme.muted, fontSize: 13, height: 1.5)),
      ],
      if (arrived) ...[
        SwitchListTile.adaptive(
            contentPadding: EdgeInsets.zero,
            title: const Text('Analyse IA automatique'),
            subtitle: Text(_automaticAnalysis
                ? 'Analyser chaque nouvelle photo APRÈS.'
                : 'Lancez l’analyse manuellement lorsque vous en avez besoin.'),
            value: _automaticAnalysis,
            onChanged: _busy
                ? null
                : (value) => setState(() => _automaticAnalysis = value)),
        const SizedBox(height: 12),
        _heading('Preuve de collecte',
            icon: Icons.photo_camera_outlined,
            subtitle: kIsWeb
                ? 'Collectez les déchets, puis sélectionnez une photo APRÈS (JPEG ou PNG).'
                : 'Collectez les déchets, puis prenez une photo APRÈS.'),
        if (before != null || after != null)
          _evidencePhotos(visit, current: true),
        if (after == null) ...[
          if (before != null) const SizedBox(height: 12),
          Container(
              padding: const EdgeInsets.all(22),
              decoration: BoxDecoration(
                  color: GestiTheme.bg,
                  border: Border.all(color: GestiTheme.border),
                  borderRadius: BorderRadius.circular(12)),
              child: const Column(children: [
                Icon(Icons.add_photo_alternate_outlined,
                    size: 32, color: GestiTheme.primary),
                SizedBox(height: 10),
                Text('Une photo APRÈS est requise',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                        color: GestiTheme.ink, fontWeight: FontWeight.w600)),
                SizedBox(height: 5),
                Text('Cadrez le point pour rendre son état visible.',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                        color: GestiTheme.muted, fontSize: 12, height: 1.5)),
              ])),
        ],
        if (after != null) ...[
          const SizedBox(height: 8),
          TextButton.icon(
              style: TextButton.styleFrom(minimumSize: const Size(0, 48)),
              onPressed:
                  _busy || _draft != null ? null : () => _capture('AFTER'),
              icon: const Icon(Icons.refresh, size: 18),
              label: const Text(kIsWeb
                  ? 'Remplacer la photo APRÈS'
                  : 'Reprendre la photo APRÈS')),
        ],
        TextButton.icon(
            style: TextButton.styleFrom(minimumSize: const Size(0, 48)),
            onPressed: _busy || _draft != null || after != null
                ? null
                : () => _capture('BEFORE'),
            icon: const Icon(Icons.add_a_photo_outlined, size: 18),
            label: const Text('Photo AVANT facultative')),
        if (after != null) ...[
          const Divider(height: 32),
          _heading('Analyse de la photo',
              icon: Icons.auto_awesome_outlined,
              subtitle:
                  'Une aide à la vérification, distincte de votre déclaration.'),
          Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                  color: _assessmentColor(visit['analysisStatus'])
                      .withOpacity(.06),
                  borderRadius: BorderRadius.circular(12)),
              child:
                  Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Icon(
                    visit['analysisStatus'] == 'CLEANED'
                        ? Icons.check_circle_outline
                        : Icons.info_outline,
                    size: 21,
                    color: _assessmentColor(visit['analysisStatus'])),
                const SizedBox(width: 10),
                Expanded(
                    child: Text(
                        TourProgress.assessmentLabel(visit['analysisStatus']),
                        style: TextStyle(
                            color: _assessmentColor(visit['analysisStatus']),
                            fontWeight: FontWeight.w600,
                            height: 1.5))),
              ])),
          if (_analysisError != null) ...[
            const SizedBox(height: 10),
            _message(_analysisError!),
          ],
          const SizedBox(height: 10),
          const Text(
              'L’IA évalue uniquement ce qui est visible. Vous et le superviseur pouvez corriger cette observation.',
              style: TextStyle(
                  fontSize: 12, color: GestiTheme.muted, height: 1.5)),
          if (!_automaticAnalysis && visit['analysisStatus'] == null) ...[
            const SizedBox(height: 10),
            const Text('Photo enregistrée. Analyse IA à lancer manuellement.',
                style: TextStyle(color: GestiTheme.muted, fontSize: 13)),
          ],
          if (_hasSavedAnalysis(visit))
            const Padding(
                padding: EdgeInsets.only(top: 10),
                child: Text('Résultat IA enregistré',
                    style: TextStyle(
                        color: GestiTheme.muted, fontWeight: FontWeight.w600)))
          else
            Align(
                alignment: Alignment.centerLeft,
                child: TextButton.icon(
                    style: TextButton.styleFrom(minimumSize: const Size(0, 48)),
                    onPressed: _busy
                        ? null
                        : () => _analyseVisit(visit['pointId'].toString()),
                    icon: Icon(
                        visit['analysisStatus'] == 'ANALYSIS_UNAVAILABLE'
                            ? Icons.refresh
                            : Icons.auto_awesome_outlined,
                        size: 18),
                    label: Text(
                        visit['analysisStatus'] == 'ANALYSIS_UNAVAILABLE' ||
                                _analysisError != null
                            ? 'Réessayer l’analyse'
                            : 'Analyser la photo'))),
          const Divider(height: 28),
          _heading('Votre observation',
              subtitle: 'Ajoutez votre avis avant de confirmer la collecte.'),
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
        ],
      ],
      const Divider(height: 32),
      OutlinedButton.icon(
          style: OutlinedButton.styleFrom(
              foregroundColor: const Color(0xFF8D651C),
              side: const BorderSide(color: Color(0xFFE8DABF)),
              minimumSize: const Size(0, 48)),
          onPressed:
              _busy || _draft != null ? null : () => _complete(issue: true),
          icon: const Icon(Icons.report_problem_outlined, size: 19),
          label: const Text('Signaler un problème et continuer',
              textAlign: TextAlign.center)),
      const SizedBox(height: 8),
      const Text(
          'Point inaccessible ou photo impossible ? Indiquez le motif pour poursuivre la tournée.',
          style: TextStyle(color: GestiTheme.muted, fontSize: 12, height: 1.5)),
    ], highlighted: true);
  }

  Widget _overview(int done, List<Map<String, dynamic>> visits, num? elapsed) {
    final status = switch (_tour['statut']) {
      'EN_COURS' => 'En cours',
      'TERMINEE' => 'Terminée',
      'ANNULEE' => 'Annulée',
      'PLANIFIEE' => 'Planifiée',
      _ => 'Statut non renseigné',
    };
    return _card([
      Wrap(
          spacing: 10,
          runSpacing: 8,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            _badge(status,
                color: _tour['statut'] == 'ANNULEE'
                    ? GestiTheme.muted
                    : GestiTheme.primaryDark),
            Row(mainAxisSize: MainAxisSize.min, children: [
              const Icon(Icons.local_shipping_outlined,
                  size: 16, color: GestiTheme.muted),
              const SizedBox(width: 6),
              Text('${_tour['camionMatricule'] ?? 'Camion non renseigné'}',
                  style:
                      const TextStyle(color: GestiTheme.muted, fontSize: 13)),
            ]),
          ]),
      const SizedBox(height: 16),
      Text('${_tour['nom'] ?? 'Tournée de collecte'}',
          style: Theme.of(context).textTheme.headlineSmall?.copyWith(
              fontWeight: FontWeight.w700,
              color: GestiTheme.ink,
              letterSpacing: -.5)),
      const SizedBox(height: 20),
      Row(children: [
        const Expanded(
            child: Text('Progression de la tournée',
                style: TextStyle(color: GestiTheme.muted, fontSize: 13))),
        Text('$done / ${visits.length} points traités',
            style: const TextStyle(
                color: GestiTheme.primaryDark,
                fontWeight: FontWeight.w700,
                fontSize: 12)),
      ]),
      const SizedBox(height: 10),
      ClipRRect(
          borderRadius: BorderRadius.circular(10),
          child: LinearProgressIndicator(
              value: visits.isEmpty ? 0 : done / visits.length,
              minHeight: 7,
              color: GestiTheme.primary,
              backgroundColor: GestiTheme.soft)),
      const SizedBox(height: 20),
      LayoutBuilder(builder: (context, constraints) {
        final metrics = [
          _metric(
              'Durée totale estimée',
              TourProgress.duration(_tour['estimatedAtStartSeconds'] ??
                  _progress.route?['totalSeconds']),
              Icons.schedule_outlined),
          _metric(
              _tour['statut'] == 'TERMINEE'
                  ? 'Durée totale observée'
                  : 'Temps écoulé',
              elapsed == null
                  ? _tour['statut'] == 'PLANIFIEE'
                      ? 'Non démarrée'
                      : 'Non disponible'
                  : TourProgress.duration(
                      _tour['actualDurationSeconds'] ?? elapsed),
              Icons.timer_outlined),
        ];
        return constraints.maxWidth >= 380
            ? Row(children: [
                Expanded(child: metrics[0]),
                const SizedBox(width: 12),
                Expanded(child: metrics[1]),
              ])
            : Column(children: [
                metrics[0],
                const SizedBox(height: 10),
                metrics[1],
              ]);
      }),
      if (_tour['statut'] == 'TERMINEE') ...[
        const SizedBox(height: 12),
        const Text(
            'La durée observée correspond aux événements enregistrés et peut inclure les attentes ou les pauses.',
            style:
                TextStyle(color: GestiTheme.muted, fontSize: 12, height: 1.5)),
      ],
    ]);
  }

  Widget _routePanel() => _card([
        _heading('Parcours routier',
            icon: Icons.route_outlined,
            subtitle: 'Ordre planifié des points, depuis et vers le dépôt.'),
        _map(),
        const SizedBox(height: 12),
        Wrap(spacing: 14, runSpacing: 8, children: [
          _mapLegend(GestiTheme.navy, 'À visiter / dépôt'),
          _mapLegend(GestiTheme.primary, 'Point traité'),
          _mapLegend(Colors.deepOrange, 'Votre position'),
        ]),
        const Divider(height: 28),
        Text(_active ? 'ESTIMATION RESTANTE' : 'ESTIMATION DU PARCOURS',
            style: const TextStyle(
                color: GestiTheme.muted,
                fontSize: 10,
                fontWeight: FontWeight.w700,
                letterSpacing: 1)),
        const SizedBox(height: 12),
        Row(children: [
          Expanded(
              child: _routeDuration(
                  'Trajet',
                  _active
                      ? _progress.remainingTravelSeconds
                      : _progress.route?['travelSeconds'],
                  Icons.route_outlined)),
          Container(width: 1, height: 34, color: GestiTheme.border),
          const SizedBox(width: 16),
          Expanded(
              child: _routeDuration(
                  'Service aux points',
                  _active
                      ? _progress.remainingServiceSeconds
                      : _progress.route?['serviceSeconds'],
                  Icons.cleaning_services_outlined)),
        ]),
      ]);

  Widget _mapLegend(Color color, String label) =>
      Row(mainAxisSize: MainAxisSize.min, children: [
        Container(
            width: 7,
            height: 7,
            decoration: BoxDecoration(color: color, shape: BoxShape.circle)),
        const SizedBox(width: 5),
        Text(label,
            style: const TextStyle(color: GestiTheme.muted, fontSize: 10)),
      ]);

  Widget _routeDuration(String label, num? seconds, IconData icon) =>
      Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Icon(icon, size: 17, color: GestiTheme.primary),
        const SizedBox(width: 8),
        Expanded(
            child:
                Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(TourProgress.duration(seconds),
              style: const TextStyle(
                  color: GestiTheme.ink,
                  fontWeight: FontWeight.w700,
                  fontSize: 16)),
          const SizedBox(height: 3),
          Text(label,
              style: const TextStyle(color: GestiTheme.muted, fontSize: 11)),
        ])),
      ]);

  Widget _draftPanel(Map<String, dynamic>? current) => _card([
        _heading('Photo en brouillon',
            icon: Icons.cloud_upload_outlined,
            subtitle: _draft!.tourId == _id &&
                    _draft!.pointId == current?['pointId']
                ? 'Photo ${_draft!.stage == 'AFTER' ? 'APRÈS' : 'AVANT'} conservée sur cet appareil, en attente d’envoi.'
                : 'Une photo non envoyée d’un autre point ou d’une autre tournée est conservée sur cet appareil.'),
        if (_draft!.tourId == _id && _draft!.pointId == current?['pointId'])
          FilledButton.icon(
              onPressed: _busy ? null : _uploadDraft,
              style: FilledButton.styleFrom(minimumSize: const Size(0, 48)),
              icon: const Icon(Icons.cloud_upload_outlined, size: 20),
              label: const Text('Réessayer l’envoi')),
        TextButton(
            onPressed: _busy ? null : _discardDraft,
            style: TextButton.styleFrom(minimumSize: const Size(0, 48)),
            child: const Text('Supprimer le brouillon')),
      ]);

  Widget _stopHistory(List<Map<String, dynamic>> visits) => _card([
        _heading('Points de la tournée',
            icon: Icons.format_list_numbered,
            subtitle:
                'Consultez la progression, les photos et les observations.'),
        if (visits.isEmpty)
          const Text('Aucun point enregistré.',
              style: TextStyle(color: GestiTheme.muted)),
        for (var i = 0; i < visits.length; i++) ...[
          if (i > 0) const Divider(height: 1),
          _historyRow(visits[i]),
        ],
      ]);

  Widget _historyRow(Map<String, dynamic> visit) {
    final handled = TourProgress.isHandled(visit);
    final issue = visit['status'] == 'ISSUE_REPORTED';
    final current = _progress.currentVisit?['pointId'] == visit['pointId'];
    final tone = issue
        ? const Color(0xFF8D651C)
        : handled || current
            ? GestiTheme.primaryDark
            : GestiTheme.muted;
    return ExpansionTile(
        key: PageStorageKey('visit-${visit['pointId']}'),
        tilePadding: EdgeInsets.zero,
        childrenPadding: const EdgeInsets.only(bottom: 16),
        shape: const Border(),
        collapsedShape: const Border(),
        iconColor: GestiTheme.muted,
        collapsedIconColor: GestiTheme.muted,
        leading: Container(
            width: 34,
            height: 34,
            alignment: Alignment.center,
            decoration: BoxDecoration(
                color: tone.withOpacity(.08),
                borderRadius: BorderRadius.circular(10)),
            child: handled
                ? Icon(issue ? Icons.priority_high : Icons.check,
                    size: 19, color: tone)
                : Text('${(visit['order'] as num? ?? 0).toInt() + 1}',
                    style:
                        TextStyle(color: tone, fontWeight: FontWeight.w700))),
        title: Text(
            '${_progress.point(visit['pointId'].toString())['reference'] ?? visit['pointId']}',
            style: const TextStyle(
                fontSize: 14,
                color: GestiTheme.ink,
                fontWeight: FontWeight.w600)),
        subtitle: Padding(
            padding: const EdgeInsets.only(top: 3),
            child: Text(
                issue
                    ? 'Problème signalé'
                    : handled
                        ? 'Collecte déclarée'
                        : visit['status'] == 'ARRIVED'
                            ? 'Arrivé'
                            : 'À visiter',
                style: TextStyle(color: tone, fontSize: 12))),
        children: [
          if (visit['afterPhoto'] != null || visit['beforePhoto'] != null) ...[
            _evidencePhotos(visit),
            const SizedBox(height: 14),
          ],
          _record('Analyse IA',
              TourProgress.assessmentLabel(visit['analysisStatus'])),
          if (visit['driverAssessment'] != null)
            _record('Observation du chauffeur',
                TourProgress.assessmentLabel(visit['driverAssessment'])),
          if ('${visit['notes'] ?? ''}'.trim().isNotEmpty)
            _record('Note du chauffeur', '${visit['notes']}'),
          if (visit['reviewLabel'] != null)
            _record('Avis superviseur',
                TourProgress.assessmentLabel(visit['reviewLabel'])),
          if ('${visit['reviewNotes'] ?? ''}'.trim().isNotEmpty)
            _record('Note du superviseur', '${visit['reviewNotes']}'),
        ]);
  }

  Widget _record(String label, String value) => Container(
        width: double.infinity,
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
            color: GestiTheme.bg, borderRadius: BorderRadius.circular(10)),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(label,
              style: const TextStyle(color: GestiTheme.muted, fontSize: 11)),
          const SizedBox(height: 4),
          Text(value,
              style: const TextStyle(
                  color: GestiTheme.ink, fontSize: 13, height: 1.5)),
        ]),
      );

  Widget? _primaryAction() {
    String label;
    IconData icon;
    VoidCallback? onPressed;
    final visit = _progress.currentVisit;
    if (_loading) return null;
    if (_tour['statut'] == 'PLANIFIEE') {
      label = 'Démarrer la tournée';
      icon = Icons.play_arrow;
      onPressed = _busy || _progress.route == null
          ? null
          : () => _run(() => TourneeService.demarrerTournee(_id));
    } else if (_active && visit != null) {
      if (visit['status'] != 'ARRIVED') {
        label = 'Je suis arrivé';
        icon = Icons.location_on_outlined;
        onPressed = _busy
            ? null
            : () => _run(
                () => TourneeService.arrive(_id, visit['pointId'].toString()));
      } else if (visit['afterPhoto'] == null) {
        label = kIsWeb ? 'Choisir la photo APRÈS' : 'Prendre la photo APRÈS';
        icon = kIsWeb ? Icons.upload_file : Icons.camera_alt_outlined;
        onPressed = _busy || _draft != null ? null : () => _capture('AFTER');
      } else {
        label = 'Confirmer la collecte et continuer';
        icon = Icons.check_circle_outline;
        onPressed =
            _busy || _draft != null ? null : () => _complete(issue: false);
      }
    } else if (_active && _progress.allHandled) {
      label = 'Confirmer le retour et terminer';
      icon = Icons.flag_outlined;
      onPressed = _busy ? null : _finish;
    } else {
      return null;
    }
    return FilledButton.icon(
        style: FilledButton.styleFrom(
            minimumSize: const Size(0, 52),
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
            backgroundColor: GestiTheme.primaryDark,
            foregroundColor: Colors.white,
            shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12))),
        onPressed: onPressed,
        icon: Icon(icon, size: 21),
        label: Text(label, textAlign: TextAlign.center));
  }

  Widget _actionBar(Widget action, int done, int total) => Container(
        decoration: const BoxDecoration(
            color: Colors.white,
            border: Border(top: BorderSide(color: GestiTheme.border))),
        child: SafeArea(
            top: false,
            child: Padding(
                padding:
                    const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                child: Center(
                    heightFactor: 1,
                    child: ConstrainedBox(
                        constraints: const BoxConstraints(maxWidth: 1120),
                        child: LayoutBuilder(builder: (context, constraints) {
                          if (constraints.maxWidth < 640) {
                            return SizedBox(
                                width: double.infinity, child: action);
                          }
                          return Row(children: [
                            Expanded(
                                child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                  Text(
                                      _busy
                                          ? 'Enregistrement en cours…'
                                          : 'Votre prochaine action',
                                      style: const TextStyle(
                                          color: GestiTheme.ink,
                                          fontWeight: FontWeight.w600)),
                                  const SizedBox(height: 4),
                                  Text('$done / $total points traités',
                                      style: const TextStyle(
                                          color: GestiTheme.muted,
                                          fontSize: 12)),
                                ])),
                            const SizedBox(width: 24),
                            SizedBox(width: 360, child: action),
                          ]);
                        }))))),
      );

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
    final primaryAction = _primaryAction();
    return Scaffold(
      backgroundColor: GestiTheme.bg,
      appBar: AppBar(
          title:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('${_tour['reference'] ?? 'Tournée'}',
                style:
                    const TextStyle(fontSize: 17, fontWeight: FontWeight.w700)),
            Text(
                _tour['statut'] == 'TERMINEE'
                    ? 'Compte rendu de tournée'
                    : 'Exécution de la tournée',
                style: const TextStyle(fontSize: 11, color: GestiTheme.muted)),
          ]),
          backgroundColor: Colors.white,
          foregroundColor: GestiTheme.ink,
          surfaceTintColor: Colors.transparent,
          elevation: 0,
          scrolledUnderElevation: 0,
          shape: const Border(bottom: BorderSide(color: GestiTheme.border)),
          actions: [
            IconButton(
                onPressed: _busy ? null : _refresh,
                tooltip: 'Actualiser',
                icon: const Icon(Icons.refresh, size: 22)),
            IconButton(
                onPressed: () => Navigator.pushNamed(context, '/assistant'),
                tooltip: 'Assistant',
                icon: const Icon(Icons.smart_toy_outlined, size: 22)),
            const SizedBox(width: 6),
          ]),
      bottomNavigationBar: primaryAction == null
          ? null
          : _actionBar(primaryAction, done, visits.length),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : LayoutBuilder(builder: (context, constraints) {
              final wide = constraints.maxWidth >= 1000;
              final readOnly =
                  ['TERMINEE', 'ANNULEE'].contains(_tour['statut']);
              final execution = <Widget>[
                if (_draft != null) _draftPanel(current),
                if (_tour['statut'] == 'PLANIFIEE')
                  _card([
                    _heading('Prêt pour le départ',
                        icon: Icons.play_circle_outline,
                        subtitle:
                            'Consultez votre parcours, puis démarrez pour activer le suivi de la tournée.'),
                    if (_progress.route == null)
                      _message(
                          'Le superviseur doit calculer et enregistrer le parcours routier avant le départ.'),
                  ]),
                if (_active && current != null) _currentStop(current),
                if (_active && visits.isEmpty)
                  _message(
                      'Aucun suivi de point disponible pour cette tournée historique. Contactez le superviseur.'),
                if (_active && _progress.allHandled)
                  _card([
                    _heading('Tous les points sont traités. Retour au dépôt.',
                        icon: Icons.warehouse_outlined,
                        subtitle:
                            '${_progress.route?['depot']?['label'] ?? 'Dépôt configuré'}'),
                    const Text(
                        'La tournée sera terminée lorsque vous aurez confirmé votre retour au dépôt.',
                        style: TextStyle(color: GestiTheme.muted, height: 1.5)),
                    const SizedBox(height: 16),
                    OutlinedButton.icon(
                        style: OutlinedButton.styleFrom(
                            minimumSize: const Size(0, 48)),
                        onPressed: _busy ||
                                TourProgress.coordinate(
                                        _progress.destination) ==
                                    null
                            ? null
                            : _navigate,
                        icon: const Icon(Icons.near_me_outlined, size: 20),
                        label: const Text('Naviguer vers le dépôt')),
                  ], highlighted: true),
                if (_tour['statut'] == 'TERMINEE')
                  _card([
                    _heading('Tournée terminée',
                        icon: Icons.task_alt,
                        subtitle:
                            'Le superviseur peut consulter les observations et les preuves.'),
                    const Text(
                        'Retrouvez ci-dessous le compte rendu de chaque point.',
                        style:
                            TextStyle(color: GestiTheme.muted, fontSize: 13)),
                  ], highlighted: true),
                if (_tour['statut'] == 'ANNULEE')
                  _card([
                    _heading('Tournée annulée',
                        icon: Icons.cancel_outlined,
                        subtitle:
                            'Les informations déjà enregistrées restent consultables.'),
                  ]),
                if (readOnly) _stopHistory(visits),
              ];
              final details = <Widget>[
                _routePanel(),
                if (_active) _tracking(),
                if (!readOnly) _stopHistory(visits),
              ];
              return RefreshIndicator(
                  onRefresh: _refresh,
                  color: GestiTheme.primary,
                  child: ListView(
                      physics: const AlwaysScrollableScrollPhysics(),
                      padding: EdgeInsets.all(wide ? 28 : 16),
                      children: [
                        Center(
                            child: ConstrainedBox(
                                constraints:
                                    const BoxConstraints(maxWidth: 1120),
                                child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.stretch,
                                    children: [
                                      if (_busy) ...[
                                        const LinearProgressIndicator(
                                            color: GestiTheme.primary,
                                            backgroundColor: GestiTheme.soft),
                                        const Padding(
                                            padding: EdgeInsets.symmetric(
                                                vertical: 10),
                                            child: Text(
                                                'Enregistrement / analyse en cours…',
                                                style: TextStyle(
                                                    color: GestiTheme.muted,
                                                    fontSize: 12))),
                                      ],
                                      if (_error != null)
                                        _message(_error!, error: true),
                                      if (_storageWarning != null)
                                        _message(_storageWarning!),
                                      if (_tour['routeError'] != null)
                                        _message('${_tour['routeError']}'),
                                      _overview(done, visits, elapsed),
                                      if (wide)
                                        Row(
                                            crossAxisAlignment:
                                                CrossAxisAlignment.start,
                                            children: [
                                              Expanded(
                                                  flex: 3,
                                                  child: Column(
                                                      crossAxisAlignment:
                                                          CrossAxisAlignment
                                                              .stretch,
                                                      children: execution)),
                                              const SizedBox(width: 20),
                                              Expanded(
                                                  flex: 2,
                                                  child: Column(
                                                      crossAxisAlignment:
                                                          CrossAxisAlignment
                                                              .stretch,
                                                      children: details)),
                                            ])
                                      else ...[
                                        ...execution,
                                        ...details,
                                      ],
                                    ]))),
                      ]));
            }),
    );
  }
}
