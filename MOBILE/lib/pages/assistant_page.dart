import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:speech_to_text/speech_to_text.dart' as stt;
import 'package:flutter_tts/flutter_tts.dart';
import '../core/app_theme.dart';
import '../services/api_service.dart';
import '../widgets/rag_source_card.dart';

class AssistantPage extends StatefulWidget {
  const AssistantPage({super.key});
  @override
  State<AssistantPage> createState() => _AssistantPageState();
}

class _AssistantPageState extends State<AssistantPage> {
  final _scaffoldKey = GlobalKey<ScaffoldState>();
  final _latestReplyKey = GlobalKey();
  final _controller = TextEditingController();
  final _focus = FocusNode();
  final _scroll = ScrollController();
  final List<Map<String, dynamic>> _messages = [];
  List<Map<String, dynamic>> _history = [];
  String? _conversationId;
  String? _historyError;
  String _historyFilter = '';
  bool _loading = false;
  bool _loadingHistory = false;

  // Voice capabilities (Speech-to-Text & Text-to-Speech)
  final stt.SpeechToText _speech = stt.SpeechToText();
  final FlutterTts _flutterTts = FlutterTts();
  bool _speechAvailable = false;
  bool _isListening = false;
  bool _isSpeaking = false;
  String? _currentlySpeakingText;
  bool _autoSpeak = false;

  static bool _isArabicScript(String text) {
    return RegExp(r'[\u0600-\u06FF\u0750-\u077F\u08A0-\u08FF]').hasMatch(text);
  }

  static const _suggestions = [
    (
      Icons.route_outlined,
      'Tournée mte3i',
      'Kifech najm nchouf el tournee mte3i ?'
    ),
    (
      Icons.delete_outline,
      'Bac mkasser',
      'Chnowa naamel ken el bac mkasser ?'
    ),
    (
      Icons.wrong_location_outlined,
      'Point inaccessible',
      'Que faire si un point de collecte est inaccessible ?'
    ),
    (
      Icons.photo_camera_outlined,
      'Photos de collecte',
      'Quelle photo est obligatoire pour confirmer une collecte normale ?'
    ),
    (
      Icons.edit_note_outlined,
      'Rédiger un message',
      'Aide-moi à rédiger un message au superviseur pour signaler un point inaccessible.'
    ),
  ];

  @override
  void initState() {
    super.initState();
    _initVoice();
    _loadHistory();
  }

  Future<void> _initVoice() async {
    try {
      _speechAvailable = await _speech.initialize(
        onError: (e) {
          if (mounted) setState(() => _isListening = false);
        },
        onStatus: (status) {
          if (status == 'done' || status == 'notListening') {
            if (mounted) setState(() => _isListening = false);
          }
        },
      );
    } catch (_) {
      _speechAvailable = false;
    }

    try {
      await _flutterTts.setSpeechRate(0.5);
      await _flutterTts.setVolume(1.0);
      await _flutterTts.setPitch(1.0);
      _flutterTts.setCompletionHandler(() {
        if (mounted) {
          setState(() {
            _isSpeaking = false;
            _currentlySpeakingText = null;
          });
        }
      });
      _flutterTts.setErrorHandler((msg) {
        if (mounted) {
          setState(() {
            _isSpeaking = false;
            _currentlySpeakingText = null;
          });
        }
      });
    } catch (_) {}
  }

  Future<void> _toggleListening() async {
    if (_isSpeaking) {
      await _flutterTts.stop();
      if (mounted) {
        setState(() {
          _isSpeaking = false;
          _currentlySpeakingText = null;
        });
      }
    }

    if (!_speechAvailable) {
      await _initVoice();
    }

    if (_isListening) {
      await _speech.stop();
      if (mounted) setState(() => _isListening = false);
      return;
    }

    if (!_speechAvailable) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Microphone ou reconnaissance vocale indisponible.'),
        ));
      }
      return;
    }

    setState(() => _isListening = true);
    HapticFeedback.lightImpact();

    await _speech.listen(
      onResult: (result) {
        if (mounted) {
          setState(() {
            _controller.text = result.recognizedWords;
            _controller.selection =
                TextSelection.collapsed(offset: _controller.text.length);
          });
        }
      },
      listenOptions: stt.SpeechListenOptions(
        listenMode: stt.ListenMode.dictation,
        cancelOnError: true,
        partialResults: true,
        autoPunctuation: true,
      ),
    );
  }

  Future<void> _speak(String text) async {
    if (_isSpeaking && _currentlySpeakingText == text) {
      await _flutterTts.stop();
      if (mounted) {
        setState(() {
          _isSpeaking = false;
          _currentlySpeakingText = null;
        });
      }
      return;
    }

    if (_isSpeaking) {
      await _flutterTts.stop();
    }

    if (text.trim().isEmpty) return;

    final isArabic = _isArabicScript(text);
    try {
      await _flutterTts.setLanguage(isArabic ? 'ar' : 'fr-FR');
    } catch (_) {}

    // Nettoyer les balises Markdown et liens pour une lecture fluide
    final cleanText = text
        .replaceAll(RegExp(r'[*_#`~\[\]]'), '')
        .replaceAll(RegExp(r'\(http[^)]+\)'), '')
        .replaceAll(RegExp(r'<!--.*?-->'), '')
        .trim();

    if (mounted) {
      setState(() {
        _isSpeaking = true;
        _currentlySpeakingText = text;
      });
    }

    await _flutterTts.speak(cleanText);
  }

  Future<void> _loadHistory() async {
    if (!mounted) return;
    setState(() {
      _loadingHistory = true;
      _historyError = null;
    });
    try {
      final response = await ApiService.instance.get('/ai/rag/conversations');
      if (!mounted) return;
      final data = response.data;
      if (data is! List) {
        throw const FormatException('Historique indisponible.');
      }
      setState(() => _history = data
          .whereType<Map>()
          .map((item) => Map<String, dynamic>.from(item))
          .toList());
    } catch (error) {
      if (mounted) {
        setState(() => _historyError = ApiService.errorMessage(error));
      }
    } finally {
      if (mounted) setState(() => _loadingHistory = false);
    }
  }

  void _closeHistory() {
    if (_scaffoldKey.currentState?.isEndDrawerOpen ?? false) {
      Navigator.of(context).pop();
    }
  }

  void _startNewChat() {
    if (_loading) return;
    _closeHistory();
    setState(() {
      _conversationId = null;
      _messages.clear();
    });
    if (_scroll.hasClients) _scroll.jumpTo(0);
  }

  void _loadConversation(Map<String, dynamic> conversation) {
    if (_loading) return;
    _closeHistory();
    setState(() {
      _conversationId = conversation['id'] as String?;
      _messages
        ..clear()
        ..addAll(((conversation['messages'] as List?) ?? [])
            .whereType<Map>()
            .map((item) => Map<String, dynamic>.from(item)));
    });
    _showLatestReply();
  }

  void _showLatestReply() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final target = _latestReplyKey.currentContext;
      if (target != null) {
        Scrollable.ensureVisible(target,
            duration: const Duration(milliseconds: 220), alignment: 0.04);
      } else if (_scroll.hasClients) {
        _scroll.animateTo(_scroll.position.maxScrollExtent,
            duration: const Duration(milliseconds: 220), curve: Curves.easeOut);
      }
    });
  }

  void _showPendingQuestion() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && _scroll.hasClients) {
        _scroll.animateTo(_scroll.position.maxScrollExtent,
            duration: const Duration(milliseconds: 180), curve: Curves.easeOut);
      }
    });
  }

  Future<void> _sendQuestion([String? text]) async {
    final query = (text ?? _controller.text).trim();
    if (query.isEmpty || query.length > 2000 || _loading) return;
    setState(() {
      _messages.add({'sender': 'USER', 'content': query});
      _loading = true;
    });
    _controller.clear();
    _focus.unfocus();
    _showPendingQuestion();
    try {
      final response = await ApiService.instance.post('/ai/rag/query', data: {
        'question': query,
        'conversationId': _conversationId,
      });
      if (!mounted) return;
      final data = response.data;
      if (data is! Map ||
          !const {
            'ANSWERED',
            'SOURCE_EXCERPTS',
            'INSUFFICIENT_INFORMATION',
            'PROVIDER_UNAVAILABLE'
          }.contains(data['status'])) {
        throw const FormatException(
            'La réponse reçue est inexploitable. Réessayez.');
      }
      final answer = (data['answer'] as String?) ?? '';
      setState(() {
        _conversationId = data['conversationId'] as String? ?? _conversationId;
        _messages.add({
          'sender': 'ASSISTANT',
          'content': answer,
          'sources': data['sources'] ?? [],
          'status': data['status'],
          'responseKind': data['responseKind'] ?? 'GROUNDED',
        });
      });
      _loadHistory();
      if (_autoSpeak && answer.isNotEmpty) {
        _speak(answer);
      }
    } catch (error) {
      if (!mounted) return;
      setState(() => _messages.add({
            'sender': 'ASSISTANT',
            'status': 'PROVIDER_UNAVAILABLE',
            'content':
                'Je n’ai pas pu obtenir une réponse pour le moment. ${ApiService.errorMessage(error)}',
            'sources': []
          }));
    } finally {
      if (mounted) {
        setState(() => _loading = false);
        _showLatestReply();
      }
    }
  }

  String _questionBefore(int index) {
    for (var i = index - 1; i >= 0; i--) {
      if (_messages[i]['sender'] == 'USER') {
        return '${_messages[i]['content'] ?? ''}';
      }
    }
    return '';
  }

  Future<void> _copy(String text) async {
    await Clipboard.setData(ClipboardData(text: text));
    if (!mounted) return;
    ScaffoldMessenger.of(context)
        .showSnackBar(const SnackBar(content: Text('Réponse copiée.')));
  }

  void _editQuestion(String question) {
    _controller.text = question;
    _controller.selection = TextSelection.collapsed(offset: question.length);
    _focus.requestFocus();
    setState(() {});
  }

  @override
  void dispose() {
    _speech.stop();
    _flutterTts.stop();
    _controller.dispose();
    _focus.dispose();
    _scroll.dispose();
    super.dispose();
  }

  Widget _historyDrawer() {
    final filtered = _history
        .where((item) => '${item['title'] ?? ''}'
            .toLowerCase()
            .contains(_historyFilter.toLowerCase()))
        .toList();
    return Drawer(
        width: 350,
        child: SafeArea(
            child: Column(children: [
          Padding(
              padding: const EdgeInsets.fromLTRB(24, 18, 12, 12),
              child: Row(children: [
                const Expanded(
                    child: Text('Vos discussions',
                        style: TextStyle(
                            fontSize: 21, fontWeight: FontWeight.w700))),
                IconButton(
                    onPressed: _closeHistory,
                    tooltip: 'Fermer l’historique',
                    icon: const Icon(Icons.close)),
              ])),
          Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: SizedBox(
                  width: double.infinity,
                  child: FilledButton.icon(
                      onPressed: _loading ? null : _startNewChat,
                      icon: const Icon(Icons.add, size: 20),
                      label: const Text('Nouvelle discussion')))),
          Padding(
              padding: const EdgeInsets.all(20),
              child: Semantics(
                  container: true,
                  explicitChildNodes: true,
                  child: TextField(
                      decoration: const InputDecoration(
                          labelText: 'Rechercher une discussion',
                          prefixIcon: Icon(Icons.search, size: 20)),
                      onChanged: (value) =>
                          setState(() => _historyFilter = value)))),
          const Divider(),
          Expanded(
              child: _loadingHistory
                  ? const Center(child: CircularProgressIndicator())
                  : _historyError != null
                      ? Padding(
                          padding: const EdgeInsets.all(24),
                          child: Column(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                Text(_historyError!,
                                    textAlign: TextAlign.center),
                                TextButton.icon(
                                    onPressed: _loadHistory,
                                    icon: const Icon(Icons.refresh),
                                    label:
                                        const Text('Réessayer l’historique')),
                              ]))
                      : filtered.isEmpty
                          ? const Center(
                              child: Padding(
                                  padding: EdgeInsets.all(24),
                                  child: Text('Aucune discussion à afficher.',
                                      style:
                                          TextStyle(color: GestiTheme.muted))))
                          : ListView.separated(
                              padding: const EdgeInsets.all(12),
                              itemCount: filtered.length,
                              separatorBuilder: (_, __) =>
                                  const SizedBox(height: 6),
                              itemBuilder: (context, index) {
                                final item = filtered[index];
                                final active = item['id'] == _conversationId;
                                final count =
                                    (item['messages'] as List?)?.length ?? 0;
                                return ListTile(
                                    shape: RoundedRectangleBorder(
                                        borderRadius:
                                            BorderRadius.circular(12)),
                                    selected: active,
                                    selectedTileColor: GestiTheme.soft,
                                    selectedColor: GestiTheme.primaryDark,
                                    leading: const Icon(
                                        Icons.chat_bubble_outline,
                                        size: 20),
                                    title: Text(
                                        '${item['title'] ?? 'Discussion'}',
                                        maxLines: 2,
                                        overflow: TextOverflow.ellipsis,
                                        style: const TextStyle(
                                            fontSize: 14,
                                            fontWeight: FontWeight.w600)),
                                    subtitle: Text('$count messages',
                                        style: const TextStyle(fontSize: 12)),
                                    onTap: _loading
                                        ? null
                                        : () => _loadConversation(item));
                              })),
          const Padding(
              padding: EdgeInsets.all(20),
              child: Text('Retrouvez vos réponses et leurs sources.',
                  style: TextStyle(color: GestiTheme.muted, fontSize: 12))),
        ])));
  }

  Widget _welcome() => Padding(
      padding: const EdgeInsets.symmetric(vertical: 32),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Container(
            width: 58,
            height: 58,
            decoration: BoxDecoration(
                color: GestiTheme.soft,
                borderRadius: BorderRadius.circular(16)),
            child: const Icon(Icons.auto_stories_outlined,
                size: 28, color: GestiTheme.primaryDark)),
        const SizedBox(height: 24),
        Text('Bonjour, comment puis-je vous aider ?',
            style: Theme.of(context).textTheme.headlineMedium),
        const SizedBox(height: 12),
        const Text(
            'Posez une question, clarifiez une consigne ou préparez un message pour votre superviseur.',
            style:
                TextStyle(fontSize: 15, color: GestiTheme.muted, height: 1.6)),
        const SizedBox(height: 28),
        LayoutBuilder(builder: (context, box) {
          final width =
              box.maxWidth >= 560 ? (box.maxWidth - 12) / 2 : box.maxWidth;
          return Wrap(
              spacing: 12,
              runSpacing: 12,
              children: _suggestions
                  .map((item) => SizedBox(
                      width: width,
                      child: Material(
                          color: Colors.white,
                          clipBehavior: Clip.antiAlias,
                          shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                              side: const BorderSide(color: GestiTheme.border)),
                          child: InkWell(
                              onTap: () => _sendQuestion(item.$3),
                              child: Padding(
                                  padding: const EdgeInsets.all(18),
                                  child: Row(children: [
                                    Icon(item.$1,
                                        color: GestiTheme.primaryDark,
                                        size: 23),
                                    const SizedBox(width: 14),
                                    Expanded(
                                        child: Text(item.$2,
                                            style: const TextStyle(
                                                fontSize: 14,
                                                fontWeight: FontWeight.w600))),
                                    const Icon(Icons.arrow_forward,
                                        color: GestiTheme.muted, size: 18),
                                  ]))))))
                  .toList());
        }),
        const SizedBox(height: 24),
        const Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Icon(Icons.source_outlined, size: 17, color: GestiTheme.muted),
          SizedBox(width: 9),
          Expanded(
              child: Text(
                  'Les consignes viennent des documents approuvés. Les sources restent à portée de main, et les brouillons sont signalés.',
                  style: TextStyle(
                      fontSize: 12, color: GestiTheme.muted, height: 1.5))),
        ]),
      ]));

  Widget _status(String status, String responseKind) {
    if (status == 'ANSWERED' && responseKind == 'CONVERSATION') {
      return const SizedBox.shrink();
    }
    if (status == 'ANSWERED' && responseKind == 'DRAFT') {
      return const Row(children: [
        Icon(Icons.edit_note_outlined, size: 17, color: GestiTheme.muted),
        SizedBox(width: 7),
        Expanded(
            child: Text('Brouillon à adapter · à relire avant envoi',
                style: TextStyle(fontSize: 12, color: GestiTheme.muted))),
      ]);
    }
    final (label, icon, color) = switch (status) {
      'ANSWERED' => (
          'Réponse documentée',
          Icons.check_circle_outline,
          GestiTheme.primaryDark
        ),
      'SOURCE_EXCERPTS' => (
          'Réponse IA momentanément indisponible',
          Icons.library_books_outlined,
          GestiTheme.amber
        ),
      'PROVIDER_UNAVAILABLE' => (
          'Service indisponible',
          Icons.cloud_off_outlined,
          GestiTheme.amber
        ),
      _ => (
          'Information non disponible dans les sources',
          Icons.info_outline,
          GestiTheme.muted
        ),
    };
    return Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Icon(icon, size: 17, color: color),
      const SizedBox(width: 7),
      Expanded(
          child: Text(label,
              style: TextStyle(
                  fontSize: 12, fontWeight: FontWeight.w600, color: color)))
    ]);
  }

  Widget _sources(List<Map<String, dynamic>> sources, bool excerpts) {
    final documentNames = sources
        .map((source) => '${source['documentName'] ?? 'Document'}')
        .toSet();
    final count = sources.length;
    return Padding(
        padding: const EdgeInsets.only(top: 16),
        child: DecoratedBox(
            decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: GestiTheme.border)),
            child: ExpansionTile(
                tilePadding: const EdgeInsets.symmetric(horizontal: 14),
                childrenPadding: const EdgeInsets.fromLTRB(12, 0, 12, 4),
                shape: const Border(),
                collapsedShape: const Border(),
                leading: const Icon(Icons.source_outlined,
                    size: 20, color: GestiTheme.primaryDark),
                title: Text(
                    '${excerpts ? 'Consulter les extraits' : 'Voir les sources'} · $count passage${count > 1 ? 's' : ''}',
                    style: const TextStyle(
                        fontSize: 13, fontWeight: FontWeight.w600)),
                subtitle: Text(
                    '${documentNames.length} document${documentNames.length > 1 ? 's' : ''}',
                    style:
                        const TextStyle(fontSize: 12, color: GestiTheme.muted)),
                children: [
                  for (var i = 0; i < sources.length; i++)
                    RagSourceCard(
                        key: ValueKey(
                            '${sources[i]['documentId']}:${sources[i]['chunkId'] ?? sources[i]['snippet']}'),
                        source: sources[i]),
                ])));
  }

  Widget _message(int index) {
    final message = _messages[index];
    final isUser = message['sender'] == 'USER';
    final content = '${message['content'] ?? ''}';
    final isArabic = _isArabicScript(content);
    if (isUser) {
      return Align(
          alignment: Alignment.centerRight,
          child: Container(
              constraints: const BoxConstraints(maxWidth: 680),
              margin: const EdgeInsets.only(top: 20, bottom: 24, left: 28),
              padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
              decoration: BoxDecoration(
                  color: GestiTheme.soft,
                  border: Border.all(color: const Color(0xFFD5E9DF)),
                  borderRadius: const BorderRadius.only(
                      topLeft: Radius.circular(16),
                      topRight: Radius.circular(16),
                      bottomLeft: Radius.circular(16),
                      bottomRight: Radius.circular(4))),
              child: Directionality(
                  textDirection: isArabic ? TextDirection.rtl : TextDirection.ltr,
                  child: SelectableText(content,
                      textAlign: isArabic ? TextAlign.right : TextAlign.left,
                      style: const TextStyle(height: 1.55)))));
    }
    final sources = ((message['sources'] as List?) ?? [])
        .whereType<Map>()
        .map((item) => Map<String, dynamic>.from(item))
        .toList();
    final status = '${message['status'] ?? 'INSUFFICIENT_INFORMATION'}';
    final responseKind = '${message['responseKind'] ?? 'GROUNDED'}';
    final excerpts = status == 'SOURCE_EXCERPTS' && sources.isNotEmpty;
    final noticeBeforeAnswer = status != 'ANSWERED' || responseKind == 'DRAFT';
    final question = _questionBefore(index);
    final retry = index == _messages.length - 1 &&
        (status == 'PROVIDER_UNAVAILABLE' || status == 'SOURCE_EXCERPTS');
    return Container(
        margin: const EdgeInsets.only(bottom: 24),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(
              key: index ==
                      _messages
                          .lastIndexWhere((item) => item['sender'] != 'USER')
                  ? _latestReplyKey
                  : null,
              children: [
                Container(
                    width: 30,
                    height: 30,
                    decoration: BoxDecoration(
                        color: GestiTheme.navy,
                        borderRadius: BorderRadius.circular(9)),
                    child: const Icon(Icons.auto_stories_outlined,
                        size: 17, color: Colors.white)),
                const SizedBox(width: 10),
                const Text('GestiCollecte',
                    style: TextStyle(fontWeight: FontWeight.w700))
              ]),
          const SizedBox(height: 14),
          if (noticeBeforeAnswer) ...[
            _status(status, responseKind),
            const SizedBox(height: 10),
          ],
          if (excerpts) ...[
            const Text(
                'Je n’ai pas pu générer la réponse. Voici un aperçu des passages retrouvés ; vous pouvez les ouvrir ou réessayer.',
                style: TextStyle(height: 1.6)),
            const SizedBox(height: 14),
            Container(
                width: double.infinity,
                padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
                decoration: const BoxDecoration(
                    border: Border(
                        left: BorderSide(color: GestiTheme.border, width: 3))),
                child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('Aperçu de la source',
                          style: TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.w600,
                              color: GestiTheme.muted)),
                      const SizedBox(height: 5),
                      Text('${sources.first['snippet'] ?? ''}',
                          maxLines: 3,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                              fontSize: 13,
                              height: 1.5,
                              color: GestiTheme.muted)),
                    ])),
          ] else
            Directionality(
                textDirection: isArabic ? TextDirection.rtl : TextDirection.ltr,
                child: MarkdownBody(
                    data: content,
                    selectable: true,
                    styleSheet: MarkdownStyleSheet.fromTheme(Theme.of(context))
                        .copyWith(
                            p: const TextStyle(
                                fontSize: 15, height: 1.65, color: GestiTheme.ink),
                            h1: Theme.of(context).textTheme.titleLarge,
                            h2: Theme.of(context).textTheme.titleMedium,
                            h3: Theme.of(context).textTheme.titleMedium,
                            blockquoteDecoration: BoxDecoration(
                                color: GestiTheme.soft,
                                borderRadius: BorderRadius.circular(8))),
                    sizedImageBuilder: (_) =>
                        const Text('[Illustration dans le document source]'),
                    onTapLink: (_, __, ___) => ScaffoldMessenger.of(context)
                        .showSnackBar(const SnackBar(
                            content: Text(
                                'Consultez les documents cités sous la réponse.'))))),
          if (status == 'ANSWERED' && responseKind == 'GROUNDED') ...[
            const SizedBox(height: 12),
            _status(status, responseKind),
          ],
          if (sources.isNotEmpty) _sources(sources, excerpts),
          const SizedBox(height: 8),
          Wrap(spacing: 4, children: [
            TextButton.icon(
                onPressed: () => _copy(content),
                icon: const Icon(Icons.copy_outlined, size: 16),
                label: const Text('Copier')),
            TextButton.icon(
                onPressed: () => _speak(content),
                icon: Icon(
                    _isSpeaking && _currentlySpeakingText == content
                        ? Icons.stop_circle_rounded
                        : Icons.volume_up_rounded,
                    size: 18,
                    color: _isSpeaking && _currentlySpeakingText == content
                        ? Colors.red
                        : null),
                label: Text(
                    _isSpeaking && _currentlySpeakingText == content
                        ? 'Arrêter'
                        : 'Écouter')),
            if (retry && question.isNotEmpty)
              TextButton.icon(
                  onPressed: _loading ? null : () => _sendQuestion(question),
                  icon: const Icon(Icons.refresh, size: 18),
                  label: const Text('Réessayer la réponse')),
            if (question.isNotEmpty)
              TextButton.icon(
                  onPressed: _loading ? null : () => _editQuestion(question),
                  icon: const Icon(Icons.edit_outlined, size: 16),
                  label: const Text('Préciser ma question')),
          ]),
          const SizedBox(height: 6),
          const Divider(),
        ]));
  }

  Widget _composer() => Container(
      decoration: const BoxDecoration(
          color: Colors.white,
          border: Border(top: BorderSide(color: GestiTheme.border))),
      child: SafeArea(
          top: false,
          child: Center(
              child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 928),
                  child: Padding(
                      padding: const EdgeInsets.fromLTRB(20, 14, 20, 10),
                      child: Column(mainAxisSize: MainAxisSize.min, children: [
                        Row(
                            crossAxisAlignment: CrossAxisAlignment.end,
                            children: [
                              Expanded(
                                  child: TextField(
                                      controller: _controller,
                                      focusNode: _focus,
                                      minLines: 1,
                                      maxLines: 4,
                                      maxLength: 2000,
                                      textInputAction: TextInputAction.send,
                                      decoration: InputDecoration(
                                          hintText: _isListening
                                              ? 'Écoute en cours… Parlez maintenant'
                                              : 'Écrivez ou dictez votre message…',
                                          labelText: _isListening
                                              ? 'Enregistrement vocal'
                                              : 'Votre question',
                                          counterText: '',
                                          fillColor: _isListening
                                              ? Colors.red.shade50
                                              : GestiTheme.bg),
                                      onChanged: (_) => setState(() {}),
                                      onSubmitted: (_) => _sendQuestion())),
                              const SizedBox(width: 8),
                              SizedBox(
                                  width: 48,
                                  height: 52,
                                  child: IconButton.filledTonal(
                                      style: IconButton.styleFrom(
                                          backgroundColor: _isListening
                                              ? Colors.red.shade100
                                              : GestiTheme.soft,
                                          foregroundColor: _isListening
                                              ? Colors.red.shade700
                                              : GestiTheme.primaryDark,
                                          shape: RoundedRectangleBorder(
                                              borderRadius:
                                                  BorderRadius.circular(12))),
                                      tooltip: _isListening
                                          ? 'Arrêter la dictée'
                                          : 'Dicter un message vocal',
                                      onPressed:
                                          _loading ? null : _toggleListening,
                                      icon: Icon(_isListening
                                          ? Icons.mic_rounded
                                          : Icons.mic_none_rounded))),
                              const SizedBox(width: 8),
                              SizedBox(
                                  width: 48,
                                  height: 52,
                                  child: IconButton.filled(
                                      style: IconButton.styleFrom(
                                          shape: RoundedRectangleBorder(
                                              borderRadius:
                                                  BorderRadius.circular(12))),
                                      tooltip: 'Envoyer la question',
                                      onPressed: _loading ||
                                              _controller.text.trim().isEmpty
                                          ? null
                                          : () => _sendQuestion(),
                                      icon: const Icon(
                                          Icons.arrow_upward_rounded))),
                            ]),
                        const SizedBox(height: 9),
                        const Text('Consignes sourcées · brouillons à relire',
                            textAlign: TextAlign.center,
                            style: TextStyle(
                                fontSize: 11, color: GestiTheme.muted)),
                      ]))))));

  @override
  Widget build(BuildContext context) => Scaffold(
      key: _scaffoldKey,
      appBar: AppBar(
          title: const Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Assistant'),
                Text('Votre aide au quotidien',
                    style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w400,
                        color: GestiTheme.muted))
              ]),
          actions: [
            IconButton(
                onPressed: () {
                  setState(() => _autoSpeak = !_autoSpeak);
                  ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                    duration: const Duration(seconds: 2),
                    content: Text(_autoSpeak
                        ? 'Lecture vocale automatique activée 🔊'
                        : 'Lecture vocale automatique désactivée 🔇'),
                  ));
                },
                tooltip: _autoSpeak
                    ? 'Désactiver la lecture vocale auto'
                    : 'Activer la lecture vocale auto',
                icon: Icon(_autoSpeak
                    ? Icons.volume_up_rounded
                    : Icons.volume_off_rounded)),
            IconButton(
                onPressed: _loading ? null : _startNewChat,
                tooltip: 'Nouvelle discussion',
                icon: const Icon(Icons.add_comment_outlined)),
            IconButton(
                onPressed: () {
                  _loadHistory();
                  _scaffoldKey.currentState?.openEndDrawer();
                },
                tooltip: 'Historique des discussions',
                icon: const Icon(Icons.history)),
            const SizedBox(width: 8)
          ]),
      endDrawer: _historyDrawer(),
      body: Column(children: [
        Expanded(
            child: SingleChildScrollView(
                controller: _scroll,
                keyboardDismissBehavior:
                    ScrollViewKeyboardDismissBehavior.onDrag,
                padding:
                    const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
                child: Center(
                    child: ConstrainedBox(
                        constraints: const BoxConstraints(maxWidth: 880),
                        child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              if (_messages.isEmpty) _welcome(),
                              for (var i = 0; i < _messages.length; i++)
                                _message(i),
                              if (_loading)
                                const Padding(
                                    padding: EdgeInsets.symmetric(vertical: 20),
                                    child: Row(children: [
                                      SizedBox(
                                          width: 18,
                                          height: 18,
                                          child: CircularProgressIndicator(
                                              strokeWidth: 2)),
                                      SizedBox(width: 12),
                                      Expanded(
                                          child: Text(
                                              'Je prépare votre réponse…',
                                              style: TextStyle(
                                                  color: GestiTheme.muted,
                                                  fontSize: 13))),
                                    ])),
                            ]))))),
        _composer()
      ]));
}
