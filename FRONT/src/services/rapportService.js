import api from './api';

export const rapportService = {
  downloadTourneePdf: async (tourneeId, reference) => {
    try {
      const response = await api.get(`/rapports/tournee/${tourneeId}/pdf`, {
        responseType: 'blob' // Important pour télécharger un fichier
      });
      
      // Créer une URL pour le blob et forcer le téléchargement
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `Rapport_Tournee_${reference}.pdf`);
      document.body.appendChild(link);
      link.click();
      
      // Nettoyage
      link.parentNode.removeChild(link);
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error("Erreur lors du téléchargement du PDF", error);
      throw error;
    }
  }
};
