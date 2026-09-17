import api from './api';

export const reclamationService = {
  getAll: async () => {
    const response = await api.get('/reclamations');
    return response.data;
  },
  repondre: async (id, reponse) => {
    const response = await api.post(`/reclamations/${id}/repondre`, { reponse });
    return response.data;
  }
};
