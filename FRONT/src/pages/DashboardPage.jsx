import { useState, useEffect } from 'react';
import { useAuth } from '../hooks/useAuth';
import { 
  Tooltip as RechartsTooltip, Legend, ResponsiveContainer,
  PieChart, Pie, Cell
} from 'recharts';
import { FiUsers, FiTruck, FiMap, FiAlertTriangle } from 'react-icons/fi';
import api from '../services/api';

const COLORS = ['#10b981', '#3b82f6', '#ef4444'];

const DashboardPage = () => {
  const { user } = useAuth();
  const [stats, setStats] = useState(null);
  
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchStats = async () => {
      try {
        const response = await api.get('/dashboard/stats');
        setStats(response.data);
      } catch (error) {
        setError(error.response?.data?.message || "Statistiques indisponibles. Actualisez la page pour réessayer.");
      }
    };
    fetchStats();
  }, []);

  if (error) return <p role="alert">{error}</p>;
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
          <h3>Exécution des tournées</h3>
          <p>Consultez les tournées pour comparer le temps estimé au temps enregistré et examiner les preuves de collecte.</p>
          <a href="/tournees" className="btn-primary">Voir les tournées</a>
          <p className="text-muted">Le tonnage n'est pas mesuré dans cette version.</p>
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
