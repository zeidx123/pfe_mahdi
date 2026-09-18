import 'package:flutter/material.dart';
import '../services/api_service.dart';

class AssistantPage extends StatefulWidget {
  const AssistantPage({super.key});

  @override
  State<AssistantPage> createState() => _AssistantPageState();
}

class _AssistantPageState extends State<AssistantPage> {
  final GlobalKey<ScaffoldState> _scaffoldKey = GlobalKey<ScaffoldState>();
  final TextEditingController _controller = TextEditingController();
  final List<Map<String, dynamic>> _messages = [];
  bool _loading = false;
  String? _conversationId;

  List<dynamic> _historyConversations = [];
  bool _loadingHistory = false;

  final List<String> _suggestedQuestions = [
    "Que faire si un point est inaccessible ?",
    "Quelle est la procédure en cas de débordement ?",
    "Quelles sont les consignes de sécurité en tournée ?",
    "Comment signaler un conteneur endommagé ?",
  ];

  @override
  void initState() {
    super.initState();
    _loadHistory();
  }

  Future<void> _loadHistory() async {
    try {
      setState(() => _loadingHistory = true);
      final response = await ApiService.instance.get('/ai/rag/conversations');
      if (response.statusCode == 200 && mounted) {
        setState(() {
          _historyConversations = response.data ?? [];
          _loadingHistory = false;
        });
      }
    } catch (_) {
      if (mounted) setState(() => _loadingHistory = false);
    }
  }

  void _startNewChat() {
    if (_loading) return;
    setState(() {
      _conversationId = null;
      _messages.clear();
    });
    if (_scaffoldKey.currentState?.isEndDrawerOpen ?? false) {
      Navigator.of(context).pop();
    }
  }

  void _loadConversation(Map<String, dynamic> conv) {
    if (_loading) return;
    setState(() {
      _conversationId = conv['id'];
      _messages.clear();
      final rawMsgs = (conv['messages'] as List?) ?? [];
      for (var m in rawMsgs) {
        _messages.add({
          'sender': m['sender'] ?? 'USER',
          'content': m['content'] ?? '',
          'sources': m['sources'] ?? [],
          'status': m['status'],
          'timestamp':
              DateTime.tryParse(m['timestamp'] ?? '') ?? DateTime.now(),
        });
      }
    });
    if (_scaffoldKey.currentState?.isEndDrawerOpen ?? false) {
      Navigator.of(context).pop();
    }
  }

  Future<void> _sendQuestion([String? text]) async {
    final query = text ?? _controller.text.trim();
    if (query.isEmpty || _loading) return;

    setState(() {
      _messages.add({
        'sender': 'USER',
        'content': query,
        'timestamp': DateTime.now(),
      });
      _loading = true;
    });

    _controller.clear();

    try {
      final response = await ApiService.instance.post(
        '/ai/rag/query',
        data: {
          'question': query,
          'conversationId': _conversationId,
        },
      );

      if (response.statusCode == 200) {
        final data = response.data;
        if (!mounted) return;
        setState(() {
          _conversationId = data['conversationId'];
          _messages.add({
            'sender': 'ASSISTANT',
            'content': data['answer'],
            'sources': data['sources'] ?? [],
            'status': data['status'] ?? 'INSUFFICIENT_INFORMATION',
            'timestamp': DateTime.now(),
          });
        });
        _loadHistory(); // Rafraîchir l'historique
      }
    } catch (e) {
      if (!mounted) return;
      setState(() => _messages.add({
            'sender': 'ASSISTANT',
            'status': 'PROVIDER_UNAVAILABLE',
            'content':
                'Le service est indisponible. Aucun conseil de procédure n’a été généré. Réessayez votre question.',
            'sources': [],
            'timestamp': DateTime.now(),
          }));
    } finally {
      if (mounted) {
        setState(() {
          _loading = false;
        });
      }
    }
  }

  String _statusLabel(dynamic value) => switch (value) {
        'ANSWERED' => 'Réponse fondée sur les documents',
        'SOURCE_EXCERPTS' => 'Extraits de sources — réponse IA indisponible',
        'PROVIDER_UNAVAILABLE' => 'Service indisponible',
        _ => 'Informations insuffisantes dans les sources',
      };

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      key: _scaffoldKey,
      appBar: AppBar(
        title: const Row(
          children: [
            Icon(Icons.smart_toy_outlined, color: Colors.white),
            SizedBox(width: 8),
            Text("Assistant"),
          ],
        ),
        backgroundColor: const Color(0xFF1E3A8A),
        foregroundColor: Colors.white,
        actions: [
          IconButton(
            icon: const Icon(Icons.add_comment_rounded),
            tooltip: "Nouveau Chat",
            onPressed: _loading ? null : _startNewChat,
          ),
          Builder(
            builder: (ctx) => IconButton(
              icon: const Icon(Icons.history_rounded),
              tooltip: "Historique des chats",
              onPressed: () {
                _loadHistory();
                Scaffold.of(ctx).openEndDrawer();
              },
            ),
          ),
        ],
      ),
      endDrawer: Drawer(
        child: Column(
          children: [
            UserAccountsDrawerHeader(
              decoration: const BoxDecoration(color: Color(0xFF1E3A8A)),
              accountName: const Text("Historique des Chats",
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
              accountEmail: const Text("Assistant RAG SmartWaste",
                  style: TextStyle(color: Colors.white70)),
              currentAccountPicture: CircleAvatar(
                backgroundColor: Colors.white,
                child: Icon(Icons.forum_rounded,
                    color: Colors.blue.shade900, size: 30),
              ),
            ),
            ListTile(
              leading: const Icon(Icons.add_circle_outline_rounded,
                  color: Color(0xFF1E3A8A)),
              title: const Text("Démarrer un nouveau chat",
                  style: TextStyle(fontWeight: FontWeight.bold)),
              onTap: _startNewChat,
            ),
            const Divider(),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Row(
                children: [
                  const Icon(Icons.history, size: 18, color: Colors.grey),
                  const SizedBox(width: 8),
                  Text(
                    "CONVERSATIONS PRÉCÉDENTES (${_historyConversations.length})",
                    style: const TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.bold,
                        color: Colors.grey),
                  ),
                ],
              ),
            ),
            Expanded(
              child: _loadingHistory
                  ? const Center(child: CircularProgressIndicator())
                  : _historyConversations.isEmpty
                      ? const Center(
                          child: Text("Aucune conversation archivée",
                              style: TextStyle(color: Colors.grey)),
                        )
                      : ListView.separated(
                          itemCount: _historyConversations.length,
                          separatorBuilder: (_, __) => const Divider(height: 1),
                          itemBuilder: (context, index) {
                            final conv = _historyConversations[index];
                            final isCurrent = conv['id'] == _conversationId;
                            final title = conv['title'] ?? 'Discussion';
                            final msgCount =
                                (conv['messages'] as List?)?.length ?? 0;

                            return ListTile(
                              selected: isCurrent,
                              selectedTileColor: Colors.blue.shade50,
                              leading: Icon(
                                Icons.chat_bubble_outline_rounded,
                                color: isCurrent
                                    ? const Color(0xFF1E3A8A)
                                    : Colors.black54,
                              ),
                              title: Text(
                                title,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: TextStyle(
                                  fontWeight: isCurrent
                                      ? FontWeight.bold
                                      : FontWeight.normal,
                                  color: isCurrent
                                      ? const Color(0xFF1E3A8A)
                                      : Colors.black87,
                                ),
                              ),
                              subtitle: Text("$msgCount message(s)",
                                  style: const TextStyle(fontSize: 11)),
                              onTap: () => _loadConversation(conv),
                            );
                          },
                        ),
            ),
          ],
        ),
      ),
      body: Column(
        children: [
          // Suggestions rapides si pas de messages
          if (_messages.isEmpty)
            Container(
              padding: const EdgeInsets.all(16),
              color: Colors.blue.shade50,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Row(
                    children: [
                      Icon(Icons.lightbulb_outline, color: Color(0xFF1E3A8A)),
                      SizedBox(width: 6),
                      Text(
                        "Questions fréquentes Chauffeur :",
                        style: TextStyle(
                            fontWeight: FontWeight.bold,
                            color: Color(0xFF1E3A8A)),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: _suggestedQuestions.map((q) {
                      return ActionChip(
                        avatar: const Icon(Icons.help_outline, size: 16),
                        label: Text(q, style: const TextStyle(fontSize: 12)),
                        onPressed: () => _sendQuestion(q),
                        backgroundColor: Colors.white,
                        elevation: 1,
                      );
                    }).toList(),
                  ),
                ],
              ),
            ),

          // Stream de messages
          Expanded(
            child: ListView.builder(
              padding: const EdgeInsets.all(16),
              itemCount: _messages.length,
              itemBuilder: (context, index) {
                final msg = _messages[index];
                final isUser = msg['sender'] == 'USER';
                final sources = (msg['sources'] as List?) ?? [];

                return Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: Row(
                    mainAxisAlignment: isUser
                        ? MainAxisAlignment.end
                        : MainAxisAlignment.start,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if (!isUser)
                        CircleAvatar(
                          backgroundColor: Colors.blue.shade800,
                          radius: 16,
                          child: const Icon(Icons.smart_toy,
                              size: 18, color: Colors.white),
                        ),
                      if (!isUser) const SizedBox(width: 8),
                      Flexible(
                        child: Container(
                          padding: const EdgeInsets.all(14),
                          decoration: BoxDecoration(
                            color: isUser
                                ? const Color(0xFF2563EB)
                                : Colors.grey.shade100,
                            borderRadius: BorderRadius.only(
                              topLeft: const Radius.circular(16),
                              topRight: const Radius.circular(16),
                              bottomLeft: Radius.circular(isUser ? 16 : 4),
                              bottomRight: Radius.circular(isUser ? 4 : 16),
                            ),
                            border: isUser
                                ? null
                                : Border.all(color: Colors.grey.shade300),
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              if (!isUser && msg['status'] != null)
                                Padding(
                                    padding: const EdgeInsets.only(bottom: 8),
                                    child: Text(_statusLabel(msg['status']),
                                        style: const TextStyle(
                                            fontWeight: FontWeight.bold,
                                            color: Color(0xFF1E3A8A)))),
                              SelectableText(
                                msg['content'] ?? '',
                                style: TextStyle(
                                  color: isUser ? Colors.white : Colors.black87,
                                  fontSize: 14,
                                  height: 1.4,
                                ),
                              ),

                              // Sources RAG Badges / Ressources documentaires
                              if (!isUser && sources.isNotEmpty) ...[
                                const Divider(height: 20),
                                const Row(
                                  children: [
                                    Icon(Icons.library_books_rounded,
                                        size: 16, color: Color(0xFF1E3A8A)),
                                    SizedBox(width: 6),
                                    Text(
                                      "Ressources documentaires consultées :",
                                      style: TextStyle(
                                          fontSize: 12,
                                          fontWeight: FontWeight.bold,
                                          color: Color(0xFF1E3A8A)),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 8),
                                Column(
                                  children: sources.map((src) {
                                    final docName =
                                        src['documentName'] ?? 'Document';
                                    final page = src['pageNumber'];
                                    final section = src['sectionTitle'] ?? '';
                                    final snippet = src['snippet'] ?? '';

                                    return Container(
                                      margin: const EdgeInsets.only(bottom: 8),
                                      padding: const EdgeInsets.all(10),
                                      decoration: BoxDecoration(
                                        color: Colors.white,
                                        borderRadius: BorderRadius.circular(8),
                                        border: Border.all(
                                            color: Colors.blue.shade200),
                                        boxShadow: [
                                          BoxShadow(
                                            color:
                                                Colors.black.withOpacity(0.02),
                                            blurRadius: 3,
                                            offset: const Offset(0, 1),
                                          ),
                                        ],
                                      ),
                                      child: Column(
                                        crossAxisAlignment:
                                            CrossAxisAlignment.start,
                                        children: [
                                          Row(
                                            children: [
                                              const Icon(
                                                  Icons.picture_as_pdf_rounded,
                                                  size: 16,
                                                  color: Colors.redAccent),
                                              const SizedBox(width: 6),
                                              Expanded(
                                                child: Text(
                                                  page is num && page > 0
                                                      ? "$docName — Page $page"
                                                      : "$docName",
                                                  style: const TextStyle(
                                                    fontSize: 12,
                                                    fontWeight: FontWeight.bold,
                                                    color: Color(0xFF0F172A),
                                                  ),
                                                  overflow:
                                                      TextOverflow.ellipsis,
                                                ),
                                              ),
                                            ],
                                          ),
                                          if (section.isNotEmpty &&
                                              section != 'Général') ...[
                                            const SizedBox(height: 4),
                                            Text(
                                              "Section : $section",
                                              style: TextStyle(
                                                fontSize: 11,
                                                fontStyle: FontStyle.italic,
                                                color: Colors.blue.shade800,
                                              ),
                                            ),
                                          ],
                                          if (snippet.isNotEmpty) ...[
                                            const SizedBox(height: 4),
                                            SelectableText(
                                              "\"$snippet\"",
                                              style: TextStyle(
                                                fontSize: 11,
                                                color: Colors.grey.shade700,
                                                height: 1.3,
                                              ),
                                            ),
                                          ],
                                        ],
                                      ),
                                    );
                                  }).toList(),
                                ),
                              ],
                            ],
                          ),
                        ),
                      ),
                      if (isUser) const SizedBox(width: 8),
                      if (isUser)
                        CircleAvatar(
                          backgroundColor: Colors.blue.shade100,
                          radius: 16,
                          child: const Icon(Icons.person,
                              size: 18, color: Color(0xFF1E3A8A)),
                        ),
                    ],
                  ),
                );
              },
            ),
          ),

          if (_loading)
            Padding(
              padding: const EdgeInsets.all(8.0),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(
                        strokeWidth: 2, color: Colors.blue.shade800),
                  ),
                  const SizedBox(width: 8),
                  const Text("Recherche dans les documents officiels...",
                      style: TextStyle(fontSize: 12, color: Colors.grey)),
                ],
              ),
            ),

          // Barre de saisie
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Colors.white,
              boxShadow: [
                BoxShadow(
                    color: Colors.black.withOpacity(0.05),
                    blurRadius: 4,
                    offset: const Offset(0, -2))
              ],
            ),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _controller,
                    decoration: InputDecoration(
                      hintText: "Posez votre question à l'assistant...",
                      contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16, vertical: 10),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(24),
                        borderSide: BorderSide(color: Colors.grey.shade300),
                      ),
                      filled: true,
                      fillColor: Colors.grey.shade50,
                    ),
                    onSubmitted: (_) => _sendQuestion(),
                  ),
                ),
                const SizedBox(width: 8),
                IconButton(
                  icon: const Icon(Icons.send, color: Color(0xFF1E3A8A)),
                  onPressed: _loading ? null : () => _sendQuestion(),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
