import api from './api';

export const camionService = {
  getAll: async () => {
    const response = await api.get('/camions');
    return response.data;
  },

  getById: async (id) => {
    const response = await api.get(`/camions/${id}`);
    return response.data;
  },

  create: async (data) => {
    const response = await api.post('/camions', data);
    return response.data;
  },

  update: async (id, data) => {
    const response = await api.put(`/camions/${id}`, data);
    return response.data;
  },

  delete: async (id) => {
    const response = await api.delete(`/camions/${id}`);
    return response.data;
  }
};
