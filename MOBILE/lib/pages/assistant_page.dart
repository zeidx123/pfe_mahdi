import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
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
  static const _suggestions = [
    (
      Icons.photo_camera_outlined,
      'Photos de collecte',
      'Quelle photo est obligatoire pour confirmer une collecte normale ?'
    ),
    (
      Icons.wrong_location_outlined,
      'Point inaccessible',
      'Que faire si un point est inaccessible ?'
    ),
    (
      Icons.route_outlined,
      'Fin de tournée',
      'Que faut-il faire après le dernier point de la tournée ?'
    ),
    (
      Icons.health_and_safety_outlined,
      'Sécurité sur le terrain',
      'Quelles sont les consignes de sécurité en tournée ?'
    ),
  ];

  @override
  void initState() {
    super.initState();
    _loadHistory();
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
      setState(() {
        _conversationId = data['conversationId'] as String? ?? _conversationId;
        _messages.add({
          'sender': 'ASSISTANT',
          'content': data['answer'] ?? '',
          'sources': data['sources'] ?? [],
          'status': data['status']
        });
      });
      _loadHistory();
    } catch (error) {
      if (!mounted) return;
      setState(() => _messages.add({
            'sender': 'ASSISTANT',
            'status': 'PROVIDER_UNAVAILABLE',
            'content':
                '${ApiService.errorMessage(error)} Aucun conseil de procédure n’a été généré.',
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
        Text('Une question sur votre collecte ?',
            style: Theme.of(context).textTheme.headlineMedium),
        const SizedBox(height: 12),
        const Text(
            'Retrouvez une consigne ou clarifiez une étape de votre tournée, à partir des documents approuvés.',
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
                  'Chaque réponse documentée vous permet de consulter ses sources. Si une information manque, l’assistant vous le signale.',
                  style: TextStyle(
                      fontSize: 12, color: GestiTheme.muted, height: 1.5))),
        ]),
      ]));

  Widget _status(String status) {
    final (label, icon, color) = switch (status) {
      'ANSWERED' => (
          'Réponse documentée',
          Icons.check_circle_outline,
          GestiTheme.primaryDark
        ),
      'SOURCE_EXCERPTS' => (
          'Extraits disponibles · réponse IA indisponible',
          Icons.library_books_outlined,
          GestiTheme.amber
        ),
      'PROVIDER_UNAVAILABLE' => (
          'Service indisponible',
          Icons.cloud_off_outlined,
          GestiTheme.amber
        ),
      _ => ('Information non trouvée', Icons.info_outline, GestiTheme.muted),
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

  Widget _message(int index) {
    final message = _messages[index];
    final isUser = message['sender'] == 'USER';
    final content = '${message['content'] ?? ''}';
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
              child: SelectableText(content,
                  style: const TextStyle(height: 1.55))));
    }
    final sources = ((message['sources'] as List?) ?? [])
        .whereType<Map>()
        .map((item) => Map<String, dynamic>.from(item))
        .toList();
    final status = '${message['status'] ?? 'INSUFFICIENT_INFORMATION'}';
    final excerpts = status == 'SOURCE_EXCERPTS' && sources.isNotEmpty;
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
          _status(status),
          const SizedBox(height: 12),
          if (excerpts)
            const Text(
                'La réponse IA n’a pas pu être générée. Vous pouvez lire les passages retrouvés et ouvrir les documents ci-dessous.',
                style: TextStyle(height: 1.6))
          else
            MarkdownBody(
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
                            'Consultez les documents cités sous la réponse.')))),
          if (sources.isNotEmpty) ...[
            const SizedBox(height: 20),
            Text(
                '${sources.length} passage${sources.length > 1 ? 's' : ''} cité${sources.length > 1 ? 's' : ''}',
                style: const TextStyle(
                    fontSize: 12,
                    color: GestiTheme.muted,
                    fontWeight: FontWeight.w600)),
            const SizedBox(height: 10),
            for (var i = 0; i < sources.length; i++)
              RagSourceCard(
                  key: ValueKey(
                      '${sources[i]['documentId']}:${sources[i]['chunkId'] ?? sources[i]['snippet']}'),
                  source: sources[i],
                  initiallyExpanded: excerpts && i == 0),
          ],
          const SizedBox(height: 8),
          Wrap(spacing: 4, children: [
            TextButton.icon(
                onPressed: () => _copy(content),
                icon: const Icon(Icons.copy_outlined, size: 16),
                label: const Text('Copier')),
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
                                      decoration: const InputDecoration(
                                          hintText: 'Posez votre question…',
                                          labelText: 'Votre question',
                                          counterText: '',
                                          fillColor: GestiTheme.bg),
                                      onChanged: (_) => setState(() {}),
                                      onSubmitted: (_) => _sendQuestion())),
                              const SizedBox(width: 10),
                              SizedBox(
                                  width: 52,
                                  height: 56,
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
                        const Text(
                            'Les réponses s’appuient sur vos documents approuvés.',
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
                Text('Collecte & procédures',
                    style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w400,
                        color: GestiTheme.muted))
              ]),
          actions: [
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
                                              'Recherche des passages et préparation de la réponse…',
                                              style: TextStyle(
                                                  color: GestiTheme.muted,
                                                  fontSize: 13))),
                                    ])),
                            ]))))),
        _composer()
      ]));
}
