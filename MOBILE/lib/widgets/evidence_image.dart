import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
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
  Widget build(BuildContext context) =>
      Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(widget.label, style: const TextStyle(fontWeight: FontWeight.w600)),
        const SizedBox(height: 6),
        FutureBuilder<Uint8List>(
            future: _bytes,
            builder: (context, snapshot) {
              if (snapshot.hasError) {
                return TextButton.icon(
                    onPressed: () => setState(() => _bytes = _load()),
                    icon: const Icon(Icons.refresh),
                    label: const Text('Recharger la photo'));
              }
              if (!snapshot.hasData) {
                return const SizedBox(
                    height: 100,
                    child: Center(child: CircularProgressIndicator()));
              }
              return ClipRRect(
                  borderRadius: BorderRadius.circular(10),
                  child: Image.memory(snapshot.data!,
                      height: 170,
                      width: double.infinity,
                      fit: BoxFit.contain,
                      errorBuilder: (_, __, ___) =>
                          const Text('Photo illisible')));
            }),
      ]);
}
