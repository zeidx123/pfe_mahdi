const pages = {
  '/login': { title: 'Connexion', section: 'Espace superviseur' },
  '/dashboard': { title: 'Tableau de bord', section: 'Vue d’ensemble' },
  '/agents': { title: 'Chauffeurs', section: 'Ressources' },
  '/camions': { title: 'Parc de camions', section: 'Ressources' },
  '/points': { title: 'Points de collecte', section: 'Opérations' },
  '/tournees': { title: 'Tournées', section: 'Opérations' },
  '/tracking': { title: 'Suivi GPS', section: 'Opérations' },
  '/reclamations': { title: 'Réclamations', section: 'Opérations' },
  '/candidatures': { title: 'Candidatures', section: 'Recrutement' },
  '/documents-ia': { title: 'Base documentaire', section: 'Connaissances' },
};

export const getPageMeta = (pathname) => pages[pathname.replace(/\/$/, '')] || pages['/dashboard'];
