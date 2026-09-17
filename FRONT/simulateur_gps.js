const axios = require('axios');

const API_URL = 'http://localhost:8080/api/tracking/position';

// Simuler 2 camions
const camions = [
  {
    id: 'CAM-001',
    matricule: 'A-12345',
    tourneeRef: 'T-2024-001',
    lat: 33.5731, // Casablanca centre
    lng: -7.5898,
    speed: 40,
    fuel: 100,
  },
  {
    id: 'CAM-002',
    matricule: 'B-98765',
    tourneeRef: 'T-2024-002',
    lat: 33.5898, // Casablanca côte
    lng: -7.6038,
    speed: 30,
    fuel: 85,
  }
];

console.log('🚛 Démarrage du simulateur GPS...');
console.log('Envoi des données vers:', API_URL);

// Fonction pour bouger le camion aléatoirement
const moveTruck = (truck) => {
  // Petit déplacement aléatoire pour simuler le mouvement
  truck.lat += (Math.random() - 0.5) * 0.001;
  truck.lng += (Math.random() - 0.5) * 0.001;
  
  // Variation de vitesse
  truck.speed = Math.max(0, truck.speed + (Math.random() - 0.5) * 10);
  
  // Baisse de carburant
  truck.fuel = Math.max(0, truck.fuel - 0.1);

  return {
    camionId: truck.id,
    camionMatricule: truck.matricule,
    tourneeId: 'ID-TOURNEE',
    tourneeReference: truck.tourneeRef,
    latitude: truck.lat,
    longitude: truck.lng,
    vitesse: truck.speed,
    niveauCarburant: truck.fuel,
    statut: truck.speed < 5 ? 'A_LARRET' : 'EN_ROUTE'
  };
};

// Boucle d'envoi toutes les 2 secondes
setInterval(async () => {
  for (const truck of camions) {
    const payload = moveTruck(truck);
    try {
      await axios.post(API_URL, payload);
      console.log(`[${new Date().toLocaleTimeString()}] ✅ Position envoyée pour ${truck.matricule} (Vitesse: ${payload.vitesse.toFixed(0)} km/h)`);
    } catch (error) {
      console.error(`❌ Erreur d'envoi pour ${truck.matricule}:`, error.message);
    }
  }
}, 2000);
