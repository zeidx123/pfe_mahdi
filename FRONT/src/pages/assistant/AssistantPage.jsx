import React, { useState, useEffect, useRef } from 'react';
import { aiService } from '../../services/aiService';
import { FiBookOpen, FiFileText, FiCopy, FiCheck, FiSend, FiPlus, FiAlertCircle } from 'react-icons/fi';

export default function AssistantPage() {
  const [conversations, setConversations] = useState([]);
  const [currentConv, setCurrentConv] = useState(null);
  const [inputMessage, setInputMessage] = useState('');
  const [sending, setSending] = useState(false);
  const [copiedId, setCopiedId] = useState(null);
  const messagesEndRef = useRef(null);

  const isArabicScript = (text) => {
    if (!text) return false;
    const arabicRegex = /[\u0600-\u06FF\u0750-\u077F\u08A0-\u08FF]/;
    return arabicRegex.test(text);
  };

  const quickPrompts = [
    'Kifech najm nchouf el tournee mte3i ?',
    'Chnowa naamel ken el bac mkasser ?',
    'كيفاش نعمل باش نشوف tournée متاعي؟',
    'Que faire lorsqu\'un point de collecte est inaccessible ?',
    'Quelles sont les consignes de sécurité pendant une tournée ?'
  ];

  useEffect(() => {
    loadConversations();
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [currentConv, sending]);

  const loadConversations = async () => {
    try {
      const data = await aiService.getRagConversations();
      setConversations(data || []);
      if (data && data.length > 0 && !currentConv) {
        setCurrentConv(data[0]);
      }
    } catch (err) {
      console.error('Erreur chargement conversations RAG:', err);
    }
  };

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  const formatDateHeader = (ts) => {
    if (!ts) return '';
    try {
      const d = new Date(ts);
      return d.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: '2-digit' }) + ', ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch (e) {
      return '';
    }
  };

  const handleCopy = (text, id) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const handleSend = async (textToSend) => {
    const msg = textToSend || inputMessage;
    if (!msg.trim() || sending) return;

    try {
      setSending(true);
      setInputMessage('');

      const now = new Date().toISOString();
      const updatedMessages = [
        ...(currentConv?.messages || []),
        { sender: 'USER', content: msg, timestamp: now },
      ];
      setCurrentConv((prev) => ({ ...prev, messages: updatedMessages }));

      const res = await aiService.queryRag(msg, currentConv?.id || null);

      // Recharger l'historique RAG pour avoir la conversation structurée
      const allConvs = await aiService.getRagConversations();
      setConversations(allConvs || []);
      
      const found = allConvs.find(c => c.id === res.conversationId);
      if (found) {
        setCurrentConv(found);
      } else {
        // Fallback local UI update
        const botMsg = {
          sender: 'ASSISTANT',
          content: res.answer,
          sources: res.sources,
          timestamp: new Date().toISOString()
        };
        setCurrentConv(prev => ({
          ...prev,
          id: res.conversationId,
          messages: [...updatedMessages, botMsg]
        }));
      }
    } catch (err) {
      alert(err.response?.data?.message || 'Erreur lors de la communication avec l\'assistant RAG.');
    } finally {
      setSending(false);
    }
  };

  return (
    <div style={{ height: 'calc(100vh - 100px)', display: 'flex', gap: '16px', fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif' }}>
      
      {/* Sidebar Historique Conversations RAG */}
      <div style={{ width: '280px', background: '#ffffff', borderRadius: '12px', border: '1px solid #e2e8f0', display: 'flex', flexDirection: 'column', padding: '14px', flexShrink: 0 }}>
        <button
          onClick={() => setCurrentConv(null)}
          style={{
            width: '100%',
            padding: '10px 14px',
            background: '#2563eb',
            color: '#ffffff',
            fontWeight: 'bold',
            fontSize: '0.85rem',
            borderRadius: '10px',
            border: 'none',
            cursor: 'pointer',
            marginBottom: '14px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: '8px',
            boxShadow: '0 2px 4px rgba(37,99,235,0.2)'
          }}
        >
          <FiPlus /> Nouvelle discussion RAG
        </button>

        <div style={{ fontSize: '0.75rem', fontWeight: 'bold', color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '10px' }}>
          Historique des recherches
        </div>

        <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '6px' }}>
          {conversations.length === 0 ? (
            <div style={{ fontSize: '0.8rem', color: '#94a3b8', textAlign: 'center', padding: '20px 0' }}>
              Aucune recherche enregistrée.
            </div>
          ) : (
            conversations.map((c) => (
              <button
                key={c.id}
                onClick={() => setCurrentConv(c)}
                style={{
                  width: '100%',
                  textAlign: 'left',
                  padding: '10px 12px',
                  borderRadius: '8px',
                  fontSize: '0.82rem',
                  border: '1px solid',
                  borderColor: currentConv?.id === c.id ? '#bfdbfe' : 'transparent',
                  background: currentConv?.id === c.id ? '#eff6ff' : 'transparent',
                  color: currentConv?.id === c.id ? '#1e40af' : '#334155',
                  fontWeight: currentConv?.id === c.id ? 'bold' : 'normal',
                  cursor: 'pointer',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap'
                }}
              >
                🧠 {c.title || 'Question RAG'}
              </button>
            ))
          )}
        </div>
      </div>

      {/* Main Chat Container */}
      <div style={{ flex: 1, background: '#ffffff', borderRadius: '12px', border: '1px solid #e2e8f0', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        
        {/* Header Bar */}
        <div style={{ padding: '12px 20px', borderBottom: '1px solid #e2e8f0', display: 'flex', alignItems: 'center', justifyContent: 'space-between', background: '#f8fafc' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <div style={{ width: '36px', height: '36px', borderRadius: '50%', background: '#2563eb', color: '#ffffff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 'bold', fontSize: '1.1rem' }}>
              🤖
            </div>
            <div>
              <div style={{ fontWeight: 'bold', fontSize: '0.95rem', color: '#0f172a', display: 'flex', alignItems: 'center', gap: '6px' }}>
                Assistant SmartWaste <span style={{ background: '#dbeafe', color: '#1e40af', fontSize: '11px', padding: '2px 8px', borderRadius: '12px', fontWeight: '700' }}>RAG Actif</span>
              </div>
              <div style={{ fontSize: '0.75rem', color: '#16a34a', display: 'flex', alignItems: 'center', gap: '4px' }}>
                <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#16a34a', display: 'inline-block' }}></span>
                Connecté à la Base Documentaire Municipale (Recherche Vectorielle & Anti-hallucination)
              </div>
            </div>
          </div>
        </div>

        {/* Message History Feed */}
        <div style={{ flex: 1, padding: '20px 30px', overflowY: 'auto', background: '#ffffff', display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {(!currentConv || !currentConv.messages || currentConv.messages.length === 0) ? (
            <div style={{ height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', textAlign: 'center', color: '#64748b' }}>
              <div style={{ fontSize: '3rem', marginBottom: '10px' }}>📚</div>
              <h3 style={{ fontSize: '1.1rem', fontWeight: 'bold', color: '#1e293b', margin: '0 0 6px 0' }}>Assistant RAG SmartWaste</h3>
              <p style={{ fontSize: '0.85rem', color: '#64748b', maxWidth: '480px', margin: '0 0 20px 0' }}>
                Posez une question sur les règlements, procédures de collecte et consignes de sécurité. Le système recherche automatiquement dans les documents officiels indexés.
              </p>

              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', justifyContent: 'center', maxWidth: '650px' }}>
                {quickPrompts.map((qp, idx) => (
                  <button
                    key={idx}
                    onClick={() => handleSend(qp)}
                    style={{
                      padding: '8px 14px',
                      background: '#f1f5f9',
                      border: '1px solid #cbd5e1',
                      borderRadius: '20px',
                      fontSize: '0.8rem',
                      color: '#334155',
                      cursor: 'pointer',
                      transition: 'all 0.2s',
                      textAlign: 'left'
                    }}
                  >
                    💡 {qp}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            currentConv.messages.map((m, idx) => {
              const isUser = m.sender === 'USER' || m.role === 'user';
              const showDate = idx === 0 || Math.abs(new Date(m.timestamp) - new Date(currentConv.messages[idx - 1].timestamp)) > 300000;
              const isArabic = isArabicScript(m.content);

              return (
                <React.Fragment key={idx}>
                  {/* Timestamp Divider */}
                  {showDate && m.timestamp && (
                    <div style={{ textAlign: 'center', margin: '10px 0 4px 0', fontSize: '0.73rem', color: '#8e8e93', fontWeight: '500' }}>
                      {formatDateHeader(m.timestamp)}
                    </div>
                  )}

                  {/* Message Row */}
                  <div style={{ display: 'flex', justifyContent: isUser ? 'flex-end' : 'flex-start', alignItems: 'flex-start', gap: '10px' }}>
                    
                    {/* Bot Avatar */}
                    {!isUser && (
                      <div style={{ width: '32px', height: '32px', borderRadius: '50%', background: '#2563eb', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.9rem', flexShrink: 0, marginTop: '4px' }}>
                        🤖
                      </div>
                    )}

                    {/* Content Box */}
                    <div style={{ maxWidth: '75%' }}>
                      <div
                        dir={isArabic ? 'rtl' : 'ltr'}
                        style={{
                          padding: '14px 18px',
                          borderRadius: isUser ? '18px 18px 4px 18px' : '18px 18px 18px 4px',
                          background: isUser ? '#2563eb' : '#f8fafc',
                          color: isUser ? '#ffffff' : '#0f172a',
                          border: isUser ? 'none' : '1px solid #e2e8f0',
                          fontSize: '0.9rem',
                          lineHeight: '1.6',
                          wordBreak: 'break-word',
                          whiteSpace: 'pre-wrap',
                          position: 'relative',
                          textAlign: isArabic ? 'right' : 'left',
                          fontFamily: isArabic ? 'system-ui, -apple-system, Segoe UI, Roboto, Helvetica, Arial, sans-serif' : 'inherit'
                        }}
                      >
                        {m.content}

                        {!isUser && (
                          <button
                            onClick={() => handleCopy(m.content, idx)}
                            style={{
                              position: 'absolute',
                              top: '10px',
                              [isArabic ? 'left' : 'right']: '10px',
                              background: 'transparent',
                              border: 'none',
                              color: '#94a3b8',
                              cursor: 'pointer',
                              fontSize: '14px'
                            }}
                            title="Copier la réponse"
                          >
                            {copiedId === idx ? <FiCheck style={{ color: '#16a34a' }} /> : <FiCopy />}
                          </button>
                        )}
                      </div>

                      {/* Section Sources RAG (Affichée uniquement si assistant & sources présentes) */}
                      {!isUser && m.sources && m.sources.length > 0 && (
                        <div style={{ marginTop: '8px', background: '#eff6ff', border: '1px solid #bfdbfe', borderRadius: '10px', padding: '12px 14px' }}>
                          <div style={{ fontSize: '0.78rem', fontWeight: '700', color: '#1e40af', display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '6px' }}>
                            <FiBookOpen /> SOURCES CITÉES ({m.sources.length})
                          </div>
                          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                            {m.sources.map((src, sIdx) => (
                              <div key={sIdx} style={{ fontSize: '0.75rem', color: '#1e293b', background: 'white', padding: '6px 10px', borderRadius: '6px', border: '1px solid #dbeafe', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontWeight: '600' }}>
                                  <FiFileText style={{ color: '#2563eb' }} />
                                  <span>{src.documentName} — Page {src.pageNumber} ({src.sectionTitle || 'Général'})</span>
                                </div>
                                <span style={{ background: '#dcfce7', color: '#15803d', padding: '1px 6px', borderRadius: '10px', fontSize: '10px', fontWeight: '700' }}>
                                  Match {Math.round(src.score * 100)}%
                                </span>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>
                  </div>
                </React.Fragment>
              );
            })
          )}

          {/* Typing Indicator */}
          {sending && (
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: '10px' }}>
              <div style={{ width: '32px', height: '32px', borderRadius: '50%', background: '#2563eb', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.9rem' }}>
                🤖
              </div>
              <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', color: '#64748b', padding: '12px 18px', borderRadius: '18px 18px 18px 4px', fontSize: '0.85rem', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <span className="animate-spin">⚙️</span>
                <span>Recherche vectorielle dans les documents officiels et génération de la réponse...</span>
              </div>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        {/* Input Bar (Bottom) */}
        <div style={{ padding: '14px 20px', borderTop: '1px solid #e2e8f0', background: '#ffffff' }}>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              handleSend();
            }}
            style={{ display: 'flex', gap: '12px', alignItems: 'center' }}
          >
            <input
              type="text"
              placeholder="Posez une question sur les consignes, sécurité ou procédures..."
              value={inputMessage}
              onChange={(e) => setInputMessage(e.target.value)}
              disabled={sending}
              style={{
                flex: 1,
                padding: '12px 18px',
                borderRadius: '24px',
                border: '1px solid #cbd5e1',
                outline: 'none',
                fontSize: '0.9rem',
                background: '#f8fafc'
              }}
            />
            <button
              type="submit"
              disabled={sending || !inputMessage.trim()}
              style={{
                width: '42px',
                height: '42px',
                borderRadius: '50%',
                background: sending || !inputMessage.trim() ? '#cbd5e1' : '#2563eb',
                color: '#ffffff',
                border: 'none',
                cursor: sending || !inputMessage.trim() ? 'not-allowed' : 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '1.1rem',
                transition: 'all 0.2s',
                boxShadow: '0 2px 4px rgba(37,99,235,0.2)'
              }}
            >
              <FiSend />
            </button>
          </form>
        </div>

      </div>
    </div>
  );
}
