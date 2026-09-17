import api from './api';

export const alerteService = {
  getAll: async () => {
    const response = await api.get('/alertes');
    return response.data;
  },

  getUnreadCount: async () => {
    const response = await api.get('/alertes/unread-count');
    return response.data;
  },

  resolve: async (id) => {
    const response = await api.patch(`/alertes/${id}/resolve`);
    return response.data;
  }
};
