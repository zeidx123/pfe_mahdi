import { useState, useEffect } from 'react';
import { useAuth } from '../hooks/useAuth';
import { 
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip as RechartsTooltip, Legend, ResponsiveContainer,
  PieChart, Pie, Cell
} from 'recharts';
import { FiUsers, FiTruck, FiMap, FiAlertTriangle } from 'react-icons/fi';
import api from '../services/api';

const COLORS = ['#10b981', '#3b82f6', '#ef4444'];

const DashboardPage = () => {
  const { user } = useAuth();
  const [stats, setStats] = useState(null);
  
  // Toujours des données mockées pour le graphe linéaire (historique)
  // car l'historique nécessite une table de logs plus complexe
  const collectData = [
    { name: 'Lun', tonnes: 12.5 },
    { name: 'Mar', tonnes: 14.2 },
    { name: 'Mer', tonnes: 13.8 },
    { name: 'Jeu', tonnes: 15.1 },
    { name: 'Ven', tonnes: 16.4 },
    { name: 'Sam', tonnes: 18.2 },
    { name: 'Dim', tonnes: 10.1 },
  ];

  useEffect(() => {
    const fetchStats = async () => {
      try {
        const response = await api.get('/dashboard/stats');
        setStats(response.data);
      } catch (error) {
        console.error("Erreur lors de la récupération des statistiques:", error);
      }
    };
    fetchStats();
  }, []);

  if (!stats) return <div className="loading-screen">Chargement du tableau de bord...</div>;

  const truckStatusData = [
    { name: 'En tournée', value: stats.camionsEnTournee },
    { name: 'Disponibles', value: stats.camionsDisponibles },
    { name: 'En maintenance', value: stats.camionsEnMaintenance },
  ].filter(d => d.value > 0);

  return (
    <div className="dashboard">
      <div className="page-header">
        <h1>Tableau de bord</h1>
        <p>Bienvenue, {user?.prenom}. Voici le résumé de l'activité.</p>
      </div>

      {/* Cartes statistiques */}
      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-icon agents"><FiUsers /></div>
          <div className="stat-details">
            <h3>Agents Actifs (Chauffeurs)</h3>
            <p className="stat-value">{stats.agentsActifs}</p>
          </div>
        </div>
        
        <div className="stat-card">
          <div className="stat-icon trucks"><FiTruck /></div>
          <div className="stat-details">
            <h3>Camions en service</h3>
            <p className="stat-value">{stats.camionsEnTournee + stats.camionsDisponibles} / {stats.camionsTotal}</p>
          </div>
        </div>
        
        <div className="stat-card">
          <div className="stat-icon routes"><FiMap /></div>
          <div className="stat-details">
            <h3>Tournées en cours</h3>
            <p className="stat-value">{stats.tourneesEnCours}</p>
          </div>
        </div>
        
        <div className="stat-card">
          <div className="stat-icon alerts"><FiAlertTriangle /></div>
          <div className="stat-details">
            <h3>Alertes critiques non lues</h3>
            <p className="stat-value">{stats.alertesCritiques}</p>
          </div>
        </div>
      </div>

      {/* Graphiques */}
      <div className="charts-grid">
        <div className="chart-card">
          <h3>Déchets collectés (7 derniers jours)</h3>
          <div className="chart-container">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={collectData}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
                <XAxis dataKey="name" axisLine={false} tickLine={false} tick={{fill: '#64748b'}} />
                <YAxis axisLine={false} tickLine={false} tick={{fill: '#64748b'}} />
                <RechartsTooltip 
                  contentStyle={{borderRadius: '8px', border: 'none', boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)'}}
                />
                <Legend />
                <Line type="monotone" dataKey="tonnes" name="Tonnes collectées" stroke="#10b981" strokeWidth={3} dot={{r: 4, fill: '#10b981', strokeWidth: 0}} activeDot={{r: 6}} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="chart-card">
          <h3>État de la flotte</h3>
          <div className="chart-container">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={truckStatusData}
                  cx="50%"
                  cy="50%"
                  innerRadius={60}
                  outerRadius={80}
                  paddingAngle={5}
                  dataKey="value"
                >
                  {truckStatusData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                  ))}
                </Pie>
                <RechartsTooltip />
                <Legend verticalAlign="bottom" height={36}/>
              </PieChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>
    </div>
  );
};

export default DashboardPage;
