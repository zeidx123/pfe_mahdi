import api from './api';
import { API_URL } from '../utils/constants';

export const candidateService = {
  list: async () => (await api.get('/ai/candidates')).data,
  requirements: async () => (await api.get('/ai/cv/requirements')).data,
  saveRequirements: async (profile) => (await api.put('/ai/cv/requirements', profile)).data,
  upload: async (file) => {
    const body = new FormData();
    body.append('cv', file);
    return (await api.post('/ai/cv/upload', body, { headers: { 'Content-Type': 'multipart/form-data' } })).data;
  },
  review: async (id, fields) => (await api.patch(`/ai/candidates/${id}/review`, { fields })).data,
  evaluate: async (id) => (await api.post(`/ai/candidates/${id}/evaluate`)).data,
  decision: async (id, statut, notesSuperviseur) => (await api.patch(`/ai/candidates/${id}/decision`, { statut, notesSuperviseur })).data,
  download: async (candidate) => {
    const path = `/${candidate.cvFilePath || ''}`.replace(/^\/+/, '/');
    if (!path.startsWith('/uploads/cv/')) throw new Error('Fichier CV indisponible');
    const response = await api.get(`${API_URL.replace(/\/api\/?$/, '')}${path}`, { responseType: 'blob' });
    const url = URL.createObjectURL(response.data);
    const link = document.createElement('a');
    link.href = url;
    link.download = candidate.cvFileName || 'cv.pdf';
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  },
};
