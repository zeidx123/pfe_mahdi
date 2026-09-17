import 'dart:async';
import 'dart:io';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:geolocator/geolocator.dart';
import 'package:image_picker/image_picker.dart';
import 'package:latlong2/latlong.dart';
import '../core/constants.dart';
import '../services/api_service.dart';
import '../services/tournee_service.dart';
import '../services/tracking_service.dart';

class ActiveTrackingPage extends StatefulWidget {
  final Map<String, dynamic> tournee;
  const ActiveTrackingPage({super.key, required this.tournee});

  @override
  State<ActiveTrackingPage> createState() => _ActiveTrackingPageState();
}

class _ActiveTrackingPageState extends State<ActiveTrackingPage> {
  final MapController _mapController = MapController();
  LatLng? _currentPosition;
  StreamSubscription<Position>? _positionStream;
  bool _isFinishing = false;
  int _elapsedSeconds = 0;
  Timer? _timer;

  // Gestion des photos
  final Map<String, String?> _photosValidation = {}; // pointId -> chemin photo local
  final Set<String> _triggeredPoints = {}; // pointId déjà détectés à proximité
  bool _photoFinPrise = false;
  File? _photoFinFile;
  bool _isUploadingPhoto = false;
  bool _showPointsList = false;
  bool _dialogOpen = false; // Empêche les dialogues multiples simultanés

  @override
  void initState() {
    super.initState();
    _startListeningPosition();
    _startTimer();
    // Initialiser le statut des points
    final points = (widget.tournee['pointsCollecte'] as List?) ?? [];
    for (final p in points) {
      _photosValidation[p['id'].toString()] = null;
    }
  }

  void _startTimer() {
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      setState(() => _elapsedSeconds++);
    });
  }

  void _startListeningPosition() {
    _positionStream = Geolocator.getPositionStream(
      locationSettings: const LocationSettings(accuracy: LocationAccuracy.high),
    ).listen((position) {
      final ll = LatLng(position.latitude, position.longitude);
      setState(() => _currentPosition = ll);
      _mapController.move(ll, 15);

      // Détecter l'arrivée aux points de collecte
      _checkArrivalAtPoints(position);
    });
  }

  Future<void> _checkArrivalAtPoints(Position position) async {
    if (_dialogOpen || _isUploadingPhoto) return;

    final points = (widget.tournee['pointsCollecte'] as List?) ?? [];
    for (final p in points) {
      final pid = p['id'].toString();
      final pRef = p['reference'] ?? 'Point';
      
      // Si le point n'est pas encore validé et n'a pas encore déclenché l'alerte de proximité
      if (_photosValidation[pid] == null && !_triggeredPoints.contains(pid)) {
        final double? lat = p['latitude'] != null ? double.tryParse(p['latitude'].toString()) : null;
        final double? lng = p['longitude'] != null ? double.tryParse(p['longitude'].toString()) : null;
        
        if (lat != null && lng != null) {
          final distance = Geolocator.distanceBetween(
            position.latitude,
            position.longitude,
            lat,
            lng,
          );

          // Si on est à moins de 35 mètres du point de collecte
          if (distance <= 35.0) {
            _triggeredPoints.add(pid);
            _dialogOpen = true;
            HapticFeedback.vibrate();
            
            if (mounted) {
              final openCamera = await showDialog<bool>(
                context: context,
                barrierDismissible: false, // Forcer le choix
                builder: (_) => AlertDialog(
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                  title: Row(
                    children: [
                      const Icon(Icons.location_on, color: Colors.green),
                      const SizedBox(width: 8),
                      Text('Point de collecte $pRef', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                    ],
                  ),
                  content: Text(
                    'Vous êtes arrivé à proximité du point de collecte "$pRef" (${distance.toStringAsFixed(0)} mètres).\n\nSouhaitez-vous ouvrir la caméra pour valider ce point ?',
                    style: const TextStyle(fontSize: 14),
                  ),
                  actions: [
                    TextButton(
                      onPressed: () => Navigator.pop(context, false),
                      child: const Text('Plus tard', style: TextStyle(color: Colors.grey)),
                    ),
                    ElevatedButton.icon(
                      icon: const Icon(Icons.camera_alt, color: Colors.white, size: 18),
                      label: const Text('Ouvrir la caméra', style: TextStyle(color: Colors.white)),
                      style: ElevatedButton.styleFrom(backgroundColor: Colors.green),
                      onPressed: () => Navigator.pop(context, true),
                    ),
                  ],
                ),
              );

              _dialogOpen = false;

              if (openCamera == true) {
                await _prendrePhotoPoint(pid, pRef);
              }
            }
            break; // Gérer un seul point à la fois
          }
        }
      }
    }
  }

  // Ouvre la caméra en temps réel (galerie bloquée)
  Future<void> _prendrePhotoPoint(String pointId, String pointRef) async {
    final picker = ImagePicker();
    final XFile? photo = await picker.pickImage(
      source: ImageSource.camera, // Caméra uniquement, galerie bloquée
      imageQuality: 75,
      maxWidth: 1280,
      maxHeight: 960,
      preferredCameraDevice: CameraDevice.rear,
    );

    if (photo == null) return; // L'agent a annulé

    setState(() => _isUploadingPhoto = true);

    try {
      final tourneeId = widget.tournee['id'];
      final formData = FormData.fromMap({
        'photo': await MultipartFile.fromFile(photo.path, filename: 'point_$pointId.jpg'),
        'pointCollecteId': pointId,
        'tourneeId': tourneeId ?? '',
        'latitude': _currentPosition?.latitude ?? 0.0,
        'longitude': _currentPosition?.longitude ?? 0.0,
      });

      // 1. Sauvegarder la photo sur la tournée
      await ApiService.instance.post(
        '/tournees/$tourneeId/points/$pointId/photo',
        data: FormData.fromMap({
          'photo': await MultipartFile.fromFile(photo.path, filename: 'point_$pointId.jpg'),
        }),
      );

      // 2. Analyser la photo avec Gemini Vision IA
      Map<String, dynamic>? aiResult;
      try {
        final aiRes = await ApiService.instance.post(
          '/ai/vision/analyze',
          data: formData,
        );
        aiResult = aiRes.data as Map<String, dynamic>?;
      } catch (aiErr) {
        // En cas d'erreur IA, la photo reste enregistrée
      }

      setState(() {
        _photosValidation[pointId] = photo.path;
      });

      // 3. Afficher le résultat de l'analyse IA au chauffeur pour confirmation/correction
      if (mounted && aiResult != null) {
        final String status = aiResult['status'] ?? 'NORMAL';
        final String typeAnomalie = aiResult['typeAnomalie'] ?? 'CONTENEUR_NORMAL';
        final double confidence = (aiResult['confidence'] as num?)?.toDouble() ?? 0.8;
        final String desc = aiResult['description'] ?? '';
        final String analysisId = aiResult['id'] ?? '';

        final isAnomaly = status == 'ANOMALIE';
        final confidencePct = (confidence * 100).round();

        await showDialog(
          context: context,
          barrierDismissible: false,
          builder: (_) => AlertDialog(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
            title: Row(
              children: [
                Icon(
                  isAnomaly ? Icons.warning_amber_rounded : Icons.check_circle,
                  color: isAnomaly ? Colors.orange : Colors.green,
                  size: 26,
                ),
                const SizedBox(width: 8),
                const Text('Analyse IA de la Photo', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
              ],
            ),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: isAnomaly ? Colors.orange.shade50 : Colors.green.shade50,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: isAnomaly ? Colors.orange.shade200 : Colors.green.shade200),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        isAnomaly ? '🟠 $typeAnomalie' : '✅ Conteneur Normal',
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          color: isAnomaly ? Colors.orange.shade900 : Colors.green.shade900,
                          fontSize: 14,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text('Score de confiance IA : $confidencePct%', style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
                    ],
                  ),
                ),
                const SizedBox(height: 12),
                Text(desc, style: const TextStyle(fontSize: 13)),
              ],
            ),
            actions: [
              TextButton(
                onPressed: () async {
                  if (analysisId.isNotEmpty) {
                    await ApiService.instance.patch('/ai/vision/confirm/$analysisId', data: {
                      'validee': false,
                      'correctionChauffeur': 'CONTENEUR_NORMAL',
                    });
                  }
                  if (mounted) Navigator.pop(context);
                },
                child: const Text('Corriger (Ras)', style: TextStyle(color: Colors.grey)),
              ),
              ElevatedButton(
                style: ElevatedButton.styleFrom(backgroundColor: isAnomaly ? Colors.orange : Colors.green),
                onPressed: () async {
                  if (analysisId.isNotEmpty) {
                    await ApiService.instance.patch('/ai/vision/confirm/$analysisId', data: {
                      'validee': true,
                      'correctionChauffeur': null,
                    });
                  }
                  if (mounted) Navigator.pop(context);
                },
                child: const Text('Confirmer l’analyse IA', style: TextStyle(color: Colors.white)),
              ),
            ],
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Erreur upload photo: $e'), backgroundColor: Colors.red),
        );
      }
    } finally {
      setState(() => _isUploadingPhoto = false);
    }
  }

  // Prise de la photo finale (obligatoire avant de terminer)
  Future<void> _prendrePhotoFin() async {
    final picker = ImagePicker();
    final XFile? photo = await picker.pickImage(
      source: ImageSource.camera,
      imageQuality: 75,
      maxWidth: 1280,
      maxHeight: 960,
      preferredCameraDevice: CameraDevice.rear,
    );

    if (photo == null) return;

    setState(() {
      _photoFinFile = File(photo.path);
      _photoFinPrise = true;
    });
  }

  Future<void> _handleTerminer() async {
    // Étape 1 : Demander confirmation
    final confirm = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Text('Terminer la tournée ?', style: TextStyle(fontWeight: FontWeight.w700)),
        content: const Text('Confirmez-vous la fin de cette tournée de collecte ?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Annuler')),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFF16a34a)),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Confirmer', style: TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );

    if (confirm != true) return;

    // Étape 2 : Forcer la photo de fin si non encore prise
    if (!_photoFinPrise) {
      final photoPrise = await showDialog<bool>(
        context: context,
        builder: (_) => AlertDialog(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
          title: const Row(children: [
            Icon(Icons.camera_alt, color: Color(0xFF0369a1)),
            SizedBox(width: 10),
            Text('Photo de fin obligatoire', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
          ]),
          content: const Text(
            'Prenez une photo de fin de service avant de clôturer la tournée (état final du camion ou dernier point).',
            style: TextStyle(fontSize: 14),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Annuler')),
            ElevatedButton.icon(
              icon: const Icon(Icons.camera_alt, color: Colors.white),
              label: const Text('Ouvrir la caméra', style: TextStyle(color: Colors.white)),
              style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFF0369a1)),
              onPressed: () => Navigator.pop(context, true),
            ),
          ],
        ),
      );

      if (photoPrise != true) return;
      await _prendrePhotoFin();
      if (!_photoFinPrise) return; // L'agent a annulé la caméra
    }

    setState(() => _isFinishing = true);
    HapticFeedback.heavyImpact();

    try {
      // Upload de la photo de fin
      if (_photoFinFile != null) {
        final formData = FormData.fromMap({
          'photo': await MultipartFile.fromFile(_photoFinFile!.path, filename: 'fin.jpg'),
        });
        await ApiService.instance.post(
          '/tournees/${widget.tournee['id']}/fin/photo',
          data: formData,
        );
      }

      TrackingService.stopTracking();
      await TourneeService.terminerTournee(widget.tournee['id']);

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: const Row(children: [
              Icon(Icons.check_circle, color: Colors.white),
              SizedBox(width: 8),
              Text('Tournée terminée avec succès !'),
            ]),
            backgroundColor: Colors.green.shade700,
            behavior: SnackBarBehavior.floating,
          ),
        );
        Navigator.pushReplacementNamed(context, '/home');
      }
    } catch (e) {
      setState(() => _isFinishing = false);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Erreur: ${e.toString()}'), backgroundColor: Colors.red),
        );
      }
    }
  }

  String _formatDuration(int seconds) {
    final h = seconds ~/ 3600;
    final m = (seconds % 3600) ~/ 60;
    final s = seconds % 60;
    return '${h.toString().padLeft(2, '0')}:${m.toString().padLeft(2, '0')}:${s.toString().padLeft(2, '0')}';
  }

  @override
  void dispose() {
    _positionStream?.cancel();
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final points = (widget.tournee['pointsCollecte'] as List?) ?? [];
    final validatedCount = _photosValidation.values.where((v) => v != null).length;

    return Scaffold(
      body: Stack(
        children: [
          // Carte plein écran
          FlutterMap(
            mapController: _mapController,
            options: MapOptions(
              initialCenter: _currentPosition ?? const LatLng(33.5731, -7.5898),
              initialZoom: 15,
            ),
            children: [
              TileLayer(
                urlTemplate: 'https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png',
                subdomains: const ['a', 'b', 'c', 'd'],
                userAgentPackageName: 'com.municipalite.gesticollecte_mobile',
              ),
              MarkerLayer(
                markers: [
                  ...points.where((p) => p['latitude'] != null).map((p) {
                    final isValidated = _photosValidation[p['id'].toString()] != null;
                    return Marker(
                      point: LatLng(p['latitude'], p['longitude']),
                      width: 44,
                      height: 44,
                      child: GestureDetector(
                        onTap: () => _prendrePhotoPoint(p['id'].toString(), p['reference'] ?? ''),
                        child: Container(
                          decoration: BoxDecoration(
                            color: isValidated ? Colors.green : Colors.deepOrange,
                            shape: BoxShape.circle,
                            border: Border.all(color: Colors.white, width: 2),
                          ),
                          child: Icon(
                            isValidated ? Icons.check : Icons.delete_outline_rounded,
                            color: Colors.white,
                            size: 22,
                          ),
                        ),
                      ),
                    );
                  }),
                  if (_currentPosition != null)
                    Marker(
                      point: _currentPosition!,
                      width: 50,
                      height: 50,
                      child: Container(
                        decoration: BoxDecoration(
                          color: const Color(0xFF0369a1),
                          shape: BoxShape.circle,
                          border: Border.all(color: Colors.white, width: 3),
                          boxShadow: [BoxShadow(color: Colors.blue.withOpacity(0.4), blurRadius: 12, spreadRadius: 2)],
                        ),
                        child: const Icon(Icons.local_shipping_rounded, color: Colors.white, size: 24),
                      ),
                    ),
                ],
              ),
            ],
          ),

          // Barre supérieure
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(16),
                  boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.1), blurRadius: 10)],
                ),
                child: Row(
                  children: [
                    Container(
                      width: 10, height: 10,
                      decoration: const BoxDecoration(color: Colors.green, shape: BoxShape.circle),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(widget.tournee['nom'] ?? '', style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14)),
                          Text(
                            'GPS Actif · $validatedCount/${points.length} points validés',
                            style: TextStyle(fontSize: 11, color: validatedCount == points.length && points.isNotEmpty ? Colors.green : Colors.grey.shade500),
                          ),
                        ],
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                      decoration: BoxDecoration(color: const Color(0xFFF1F5F9), borderRadius: BorderRadius.circular(8)),
                      child: Text(_formatDuration(_elapsedSeconds), style: const TextStyle(fontFamily: 'monospace', fontWeight: FontWeight.w700, fontSize: 13)),
                    ),
                    const SizedBox(width: 8),
                    // Bouton afficher/masquer la liste des points
                    GestureDetector(
                      onTap: () => setState(() => _showPointsList = !_showPointsList),
                      child: Container(
                        padding: const EdgeInsets.all(6),
                        decoration: BoxDecoration(
                          color: const Color(0xFF0369a1).withOpacity(0.1),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Icon(
                          _showPointsList ? Icons.map_outlined : Icons.list_alt,
                          color: const Color(0xFF0369a1),
                          size: 20,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),

          // Panneau de liste des points de collecte (slide-up)
          if (_showPointsList)
            Positioned(
              bottom: 80,
              left: 0,
              right: 0,
              child: Container(
                margin: const EdgeInsets.symmetric(horizontal: 12),
                constraints: const BoxConstraints(maxHeight: 320),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(20),
                  boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.15), blurRadius: 20)],
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
                      decoration: const BoxDecoration(
                        color: Color(0xFF0369a1),
                        borderRadius: BorderRadius.only(topLeft: Radius.circular(20), topRight: Radius.circular(20)),
                      ),
                      child: Row(
                        children: [
                          const Icon(Icons.delete_outline_rounded, color: Colors.white, size: 18),
                          const SizedBox(width: 8),
                          Text('Points de collecte ($validatedCount/${points.length})', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
                          const Spacer(),
                          GestureDetector(
                            onTap: () => setState(() => _showPointsList = false),
                            child: const Icon(Icons.close, color: Colors.white70, size: 20),
                          ),
                        ],
                      ),
                    ),
                    Flexible(
                      child: ListView.builder(
                        shrinkWrap: true,
                        padding: const EdgeInsets.all(8),
                        itemCount: points.length,
                        itemBuilder: (context, i) {
                          final p = points[i];
                          final pid = p['id'].toString();
                          final isValidated = _photosValidation[pid] != null;
                          return Container(
                            margin: const EdgeInsets.symmetric(vertical: 4, horizontal: 4),
                            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                            decoration: BoxDecoration(
                              color: isValidated ? Colors.green.shade50 : Colors.grey.shade50,
                              borderRadius: BorderRadius.circular(12),
                              border: Border.all(color: isValidated ? Colors.green.shade200 : Colors.grey.shade200),
                            ),
                            child: Row(
                              children: [
                                Container(
                                  width: 32, height: 32,
                                  decoration: BoxDecoration(
                                    color: isValidated ? Colors.green : Colors.grey.shade300,
                                    shape: BoxShape.circle,
                                  ),
                                  child: Icon(
                                    isValidated ? Icons.check : Icons.delete_outline,
                                    color: Colors.white,
                                    size: 17,
                                  ),
                                ),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(p['reference'] ?? 'Point ${i + 1}', style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13)),
                                      if (p['adresse'] != null)
                                        Text(p['adresse'], style: TextStyle(fontSize: 11, color: Colors.grey.shade500), maxLines: 1, overflow: TextOverflow.ellipsis),
                                    ],
                                  ),
                                ),
                                if (!isValidated)
                                  ElevatedButton.icon(
                                    onPressed: _isUploadingPhoto ? null : () => _prendrePhotoPoint(pid, p['reference'] ?? ''),
                                    icon: const Icon(Icons.camera_alt, size: 16, color: Colors.white),
                                    label: const Text('Photo', style: TextStyle(color: Colors.white, fontSize: 12)),
                                    style: ElevatedButton.styleFrom(
                                      backgroundColor: const Color(0xFF0369a1),
                                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                                      minimumSize: Size.zero,
                                      tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                                    ),
                                  )
                                else
                                  const Text('Validé ✓', style: TextStyle(color: Colors.green, fontWeight: FontWeight.w600, fontSize: 12)),
                              ],
                            ),
                          );
                        },
                      ),
                    ),
                  ],
                ),
              ),
            ),

          // Indicateur d'upload en cours
          if (_isUploadingPhoto)
            Positioned.fill(
              child: Container(
                color: Colors.black45,
                child: const Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      CircularProgressIndicator(color: Colors.white),
                      SizedBox(height: 16),
                      Text('Envoi de la photo...', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
                    ],
                  ),
                ),
              ),
            ),

          // Bouton de fin en bas
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: SafeArea(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: SizedBox(
                  height: 56,
                  child: ElevatedButton.icon(
                    onPressed: _isFinishing || _isUploadingPhoto ? null : _handleTerminer,
                    icon: _isFinishing
                        ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2))
                        : const Icon(Icons.stop_circle_outlined),
                    label: Text(
                      _isFinishing ? 'Finalisation...' : 'Terminer la tournée',
                      style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
                    ),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFFdc2626),
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                      elevation: 0,
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
