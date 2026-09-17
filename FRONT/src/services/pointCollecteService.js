import api from './api';

export const pointCollecteService = {
  getAll: async () => {
    const response = await api.get('/points');
    return response.data;
  },

  getById: async (id) => {
    const response = await api.get(`/points/${id}`);
    return response.data;
  },

  create: async (data) => {
    const response = await api.post('/points', data);
    return response.data;
  },

  update: async (id, data) => {
    const response = await api.put(`/points/${id}`, data);
    return response.data;
  },

  delete: async (id) => {
    const response = await api.delete(`/points/${id}`);
    return response.data;
  }
};
