import api from './api';

export const tourneeService = {
  getAll: async () => {
    const response = await api.get('/tournees');
    return response.data;
  },
  getById: async (id) => {
    const response = await api.get(`/tournees/${id}`);
    return response.data;
  },
  create: async (data) => {
    const response = await api.post('/tournees', data);
    return response.data;
  },
  update: async (id, data) => {
    const response = await api.put(`/tournees/${id}`, data);
    return response.data;
  },
  updateStatut: async (id, statut) => {
    const response = await api.patch(`/tournees/${id}/statut?statut=${statut}`);
    return response.data;
  },
  delete: async (id) => {
    const response = await api.delete(`/tournees/${id}`);
    return response.data;
  },
  review: async (id, pointId, data) => {
    const response = await api.patch(`/tournees/${id}/points/${pointId}/review`, data);
    return response.data;
  },
  reoptimiser: async (id) => {
    const response = await api.post(`/tournees/${id}/optimiser`);
    return response.data;
  }
};
