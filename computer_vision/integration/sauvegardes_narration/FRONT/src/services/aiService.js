import api from './api';

export const aiService = {
  // 1. Agent CV
  uploadCv: async (file) => {
    const formData = new FormData();
    formData.append('cv', file);
    const res = await api.post('/ai/cv/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return res.data;
  },
  getCandidates: async () => {
    const res = await api.get('/ai/candidates');
    return res.data;
  },
  getCandidateById: async (id) => {
    const res = await api.get(`/ai/candidates/${id}`);
    return res.data;
  },
  updateCandidateDecision: async (id, statut, notesSuperviseur) => {
    const res = await api.patch(`/ai/candidates/${id}/decision`, { statut, notesSuperviseur });
    return res.data;
  },

  // 2. Agent Optimisation Tournées
  optimizeRoute: async (data) => {
    const res = await api.post('/ai/routes/optimize', data);
    return res.data;
  },
  getRouteHistory: async () => {
    const res = await api.get('/ai/routes/history');
    return res.data;
  },
  updateRouteStatus: async (id, statut) => {
    const res = await api.patch(`/ai/routes/${id}/status`, { statut });
    return res.data;
  },

  // 3. Agent Vision Photos & Problèmes
  getPhotoAnalyses: async () => {
    const res = await api.get('/ai/vision/analyses');
    return res.data;
  },
  getPhotoAnalysis: async (id) => {
    const res = await api.get(`/ai/vision/analyses/${id}`);
    return res.data;
  },
  getAiProblems: async () => {
    const res = await api.get('/ai/problems');
    return res.data;
  },
  analyzePhoto: async (formData) => {
    const res = await api.post('/ai/vision/analyze', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return res.data;
  },
  confirmPhotoPrediction: async (id, validee, correctionChauffeur) => {
    const res = await api.patch(`/ai/vision/confirm/${id}`, { validee, correctionChauffeur });
    return res.data;
  },

  // 4. Agent Assistant Superviseur & RAG
  sendChatMessage: async (message, conversationId = null) => {
    const res = await api.post('/ai/assistant/chat', { message, conversationId });
    return res.data;
  },
  getConversations: async () => {
    const res = await api.get('/ai/assistant/conversations');
    return res.data;
  },
  getSummaryStats: async () => {
    const res = await api.get('/ai/statistics/summary');
    return res.data;
  },

  // 5. Module RAG & Base Documentaire
  uploadDocument: async (file) => {
    const formData = new FormData();
    formData.append('file', file);
    const res = await api.post('/ai/documents', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return res.data;
  },
  getDocuments: async () => {
    const res = await api.get('/ai/documents');
    return res.data;
  },
  getDocumentById: async (id) => {
    const res = await api.get(`/ai/documents/${id}`);
    return res.data;
  },
  deleteDocument: async (id) => {
    const res = await api.delete(`/ai/documents/${id}`);
    return res.data;
  },
  reindexDocument: async (id) => {
    const res = await api.post(`/ai/documents/${id}/reindex`);
    return res.data;
  },
  queryRag: async (question, conversationId = null) => {
    const res = await api.post('/ai/rag/query', { question, conversationId });
    return res.data;
  },
  getRagConversations: async () => {
    const res = await api.get('/ai/rag/conversations');
    return res.data;
  },
};

