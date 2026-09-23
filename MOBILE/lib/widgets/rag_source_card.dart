import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import '../core/app_theme.dart';
import '../services/api_service.dart';
import '../services/source_document_service.dart';
import '../services/source_file_open.dart';

class RagSourceCard extends StatefulWidget {
  final Map<String, dynamic> source;
  final bool initiallyExpanded;
  const RagSourceCard(
      {super.key, required this.source, this.initiallyExpanded = false});

  @override
  State<RagSourceCard> createState() => _RagSourceCardState();
}

class _RagSourceCardState extends State<RagSourceCard> {
  bool _opening = false;
  String? _error;

  Future<void> _open() async {
    if (_opening) return;
    setState(() {
      _opening = true;
      _error = null;
    });
    try {
      final document = await SourceDocumentService.fetch(widget.source);
      if (!mounted) return;
      await openSourceFile(
          document.bytes, document.filename, document.mimeType);
    } catch (error) {
      if (mounted) setState(() => _error = ApiService.errorMessage(error));
    } finally {
      if (mounted) setState(() => _opening = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final name = '${widget.source['documentName'] ?? 'Document'}';
    final page = widget.source['pageNumber'];
    final section = '${widget.source['sectionTitle'] ?? ''}';
    final snippet = '${widget.source['snippet'] ?? ''}';
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: GestiTheme.border)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Icon(Icons.description_outlined,
              size: 18, color: GestiTheme.primaryDark),
          const SizedBox(width: 8),
          Expanded(
              child: Text(page is num && page > 0 ? '$name — Page $page' : name,
                  style: const TextStyle(
                      fontSize: 13, fontWeight: FontWeight.w600))),
        ]),
        if (section.isNotEmpty && section != 'Général') ...[
          const SizedBox(height: 4),
          Text('Section : $section',
              style: const TextStyle(fontSize: 12, color: GestiTheme.muted)),
        ],
        if (snippet.isNotEmpty)
          ExpansionTile(
            initiallyExpanded: widget.initiallyExpanded,
            tilePadding: EdgeInsets.zero,
            childrenPadding: const EdgeInsets.only(bottom: 8),
            title: const Text('Extrait cité', style: TextStyle(fontSize: 13)),
            children: [
              Align(
                  alignment: Alignment.centerLeft,
                  child: MarkdownBody(
                    data: snippet,
                    selectable: true,
                    styleSheet: MarkdownStyleSheet.fromTheme(Theme.of(context))
                        .copyWith(
                            p: const TextStyle(
                                fontSize: 13,
                                height: 1.6,
                                color: GestiTheme.ink),
                            h1: const TextStyle(
                                fontSize: 15, fontWeight: FontWeight.w600),
                            h2: const TextStyle(
                                fontSize: 14, fontWeight: FontWeight.w600),
                            h3: const TextStyle(
                                fontSize: 14, fontWeight: FontWeight.w600)),
                    sizedImageBuilder: (_) =>
                        const Text('[Illustration dans le document source]'),
                    onTapLink: (_, __, ___) => _open(),
                  )),
            ],
          ),
        TextButton.icon(
            onPressed: _opening ? null : _open,
            icon: _opening
                ? const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(strokeWidth: 2))
                : const Icon(Icons.open_in_new, size: 16),
            label: Text(_opening
                ? 'Ouverture…'
                : kIsWeb
                    ? 'Télécharger la source'
                    : 'Ouvrir le document')),
        if (_error != null)
          Text(_error!,
              style: TextStyle(color: Theme.of(context).colorScheme.error)),
      ]),
    );
  }
}
