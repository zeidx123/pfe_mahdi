import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import '../core/app_theme.dart';
import '../services/api_service.dart';

class EvidenceImage extends StatefulWidget {
  final String path;
  final String label;
  const EvidenceImage({super.key, required this.path, required this.label});
  @override
  State<EvidenceImage> createState() => _EvidenceImageState();
}

class _EvidenceImageState extends State<EvidenceImage> {
  late Future<Uint8List> _bytes;
  Future<Uint8List> _load() async {
    final result = await ApiService.instance.get<List<int>>(
        ApiService.protectedFileUrl(widget.path),
        options: Options(responseType: ResponseType.bytes));
    return Uint8List.fromList(result.data!);
  }

  @override
  void initState() {
    super.initState();
    _bytes = _load();
  }

  @override
  void didUpdateWidget(covariant EvidenceImage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.path != widget.path) _bytes = _load();
  }

  @override
  Widget build(BuildContext context) => Container(
        clipBehavior: Clip.antiAlias,
        decoration: BoxDecoration(
            color: Colors.white,
            border: Border.all(color: GestiTheme.border),
            borderRadius: BorderRadius.circular(12)),
        child:
            Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              child: Row(children: [
                const Icon(Icons.image_outlined,
                    color: GestiTheme.primaryDark, size: 17),
                const SizedBox(width: 8),
                Expanded(
                    child: Text(widget.label,
                        style: const TextStyle(
                            color: GestiTheme.ink,
                            fontSize: 12,
                            fontWeight: FontWeight.w600))),
              ])),
          Container(
            height: 200,
            color: GestiTheme.bg,
            child: FutureBuilder<Uint8List>(
                future: _bytes,
                builder: (context, snapshot) {
                  if (snapshot.hasError) {
                    return Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const Icon(Icons.broken_image_outlined,
                              color: GestiTheme.muted, size: 28),
                          const SizedBox(height: 8),
                          const Text('Photo indisponible',
                              style: TextStyle(
                                  color: GestiTheme.muted, fontSize: 12)),
                          TextButton.icon(
                              style: TextButton.styleFrom(
                                  minimumSize: const Size(0, 48)),
                              onPressed: () => setState(() => _bytes = _load()),
                              icon: const Icon(Icons.refresh, size: 18),
                              label: const Text('Recharger la photo')),
                        ]);
                  }
                  if (!snapshot.hasData) {
                    return const Center(
                        child: SizedBox(
                            width: 24,
                            height: 24,
                            child: CircularProgressIndicator(
                                color: GestiTheme.primary, strokeWidth: 2)));
                  }
                  return Image.memory(snapshot.data!,
                      width: double.infinity,
                      fit: BoxFit.contain,
                      semanticLabel: 'Photo ${widget.label}',
                      errorBuilder: (_, __, ___) => const Center(
                              child: Column(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                Icon(Icons.broken_image_outlined,
                                    color: GestiTheme.muted, size: 28),
                                SizedBox(height: 8),
                                Text('Photo illisible',
                                    style: TextStyle(
                                        color: GestiTheme.muted, fontSize: 12)),
                              ])));
                }),
          ),
        ]),
      );
}
