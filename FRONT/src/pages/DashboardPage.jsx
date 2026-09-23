import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts';
import {
  FiAlertTriangle, FiArrowRight, FiCheckCircle, FiClock, FiFileText,
  FiMap, FiMapPin, FiPlus, FiRefreshCw, FiTruck, FiUsers,
} from 'react-icons/fi';
import { useAuth } from '../hooks/useAuth';
import api from '../services/api';
import './DashboardPage.css';

const DashboardPage = () => {
  const { user } = useAuth();
  const [stats, setStats] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [refreshToken, setRefreshToken] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    const fetchStats = async () => {
      setLoading(true);
      setError('');
      try {
        const response = await api.get('/dashboard/stats', { signal: controller.signal });
        setStats(response.data);
      } catch (requestError) {
        if (!controller.signal.aborted) {
          setError(requestError.response?.data?.message || 'Les statistiques sont momentanément indisponibles.');
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    };
    fetchStats();
    return () => controller.abort();
  }, [refreshToken]);

  const fleet = stats ? [
    { name: 'En tournée', value: stats.camionsEnTournee, color: '#10b981' },
    { name: 'Disponibles', value: stats.camionsDisponibles, color: '#3b82f6' },
    { name: 'En maintenance', value: stats.camionsEnMaintenance, color: '#ef4444' },
  ] : [];
  const chartData = fleet.filter((status) => status.value > 0);

  return (
    <div className="dashboard">
      <div className="page-header dashboard__header">
        <div>
          <span className="dashboard__eyebrow">Vue d’ensemble</span>
          <h1>Tableau de bord</h1>
          <p>Bonjour{user?.prenom ? ` ${user.prenom}` : ''}, retrouvez l’essentiel de vos opérations.</p>
        </div>
        <Link to="/tournees" className="btn-primary dashboard__primary-action">
          <FiPlus aria-hidden="true" /> Planifier une tournée
        </Link>
      </div>

      {loading ? (
        <div className="dashboard__feedback" role="status"><FiRefreshCw aria-hidden="true" /> Chargement de votre activité…</div>
      ) : error ? (
        <div className="dashboard__feedback dashboard__feedback--error" role="alert">
          <FiAlertTriangle aria-hidden="true" /><p>{error}</p>
          <button type="button" className="btn-secondary" onClick={() => setRefreshToken((value) => value + 1)}>
            <FiRefreshCw aria-hidden="true" /> Réessayer
          </button>
        </div>
      ) : stats ? (
        <>
          <section className="dashboard__metrics" aria-label="Indicateurs opérationnels">
            <article className="dashboard__metric">
              <div className="dashboard__metric-heading"><span>Chauffeurs actifs</span><FiUsers aria-hidden="true" /></div>
              <strong className="dashboard__metric-value">{stats.agentsActifs}</strong>
              <span className="dashboard__metric-note">Équipe de collecte</span>
            </article>
            <article className="dashboard__metric">
              <div className="dashboard__metric-heading"><span>Camions en service</span><FiTruck aria-hidden="true" /></div>
              <strong className="dashboard__metric-value">{stats.camionsEnTournee + stats.camionsDisponibles}<small> / {stats.camionsTotal}</small></strong>
              <span className="dashboard__metric-note">Disponibles ou en tournée</span>
            </article>
            <article className="dashboard__metric">
              <div className="dashboard__metric-heading"><span>Tournées en cours</span><FiMap aria-hidden="true" /></div>
              <strong className="dashboard__metric-value">{stats.tourneesEnCours}</strong>
              <span className="dashboard__metric-note">Collectes démarrées</span>
            </article>
            <article className={`dashboard__metric${stats.alertesCritiques > 0 ? ' dashboard__metric--alert' : ''}`}>
              <div className="dashboard__metric-heading"><span>Alertes critiques</span><FiAlertTriangle aria-hidden="true" /></div>
              <strong className="dashboard__metric-value">{stats.alertesCritiques}</strong>
              <span className="dashboard__metric-note">Non résolues</span>
            </article>
          </section>

          <div className="dashboard__main-grid">
            <section className="dashboard__panel dashboard__fleet" aria-labelledby="fleet-title">
              <div className="dashboard__panel-heading">
                <div><span className="dashboard__eyebrow">Ressources</span><h2 id="fleet-title">État de la flotte</h2></div>
                <Link to="/camions" className="dashboard__text-link">Gérer <FiArrowRight aria-hidden="true" /></Link>
              </div>
              {chartData.length > 0 ? (
                <div className="dashboard__fleet-body">
                  <div className="dashboard__chart" role="img" aria-label={`Flotte : ${fleet.map((status) => `${status.value} ${status.name.toLowerCase()}`).join(', ')}.`}>
                    <ResponsiveContainer width="100%" height="100%">
                      <PieChart>
                        <Pie data={chartData} cx="50%" cy="50%" innerRadius={62} outerRadius={81} paddingAngle={chartData.length > 1 ? 5 : 0} dataKey="value" stroke="none">
                          {chartData.map((status) => <Cell key={status.name} fill={status.color} />)}
                        </Pie>
                        <Tooltip contentStyle={{ borderRadius: 8, border: '1px solid #e2e8f0', fontSize: 12 }} />
                      </PieChart>
                    </ResponsiveContainer>
                    <div className="dashboard__chart-total" aria-hidden="true"><strong>{stats.camionsTotal}</strong><span>camions actifs</span></div>
                  </div>
                  <ul className="dashboard__legend" aria-label="Répartition des camions">
                    {fleet.map((status) => (
                      <li key={status.name}><span className="dashboard__legend-dot" style={{ background: status.color }} aria-hidden="true" /><span>{status.name}</span><strong>{status.value}</strong></li>
                    ))}
                  </ul>
                </div>
              ) : (
                <div className="dashboard__fleet-empty">
                  <div className="dashboard__empty-ring" aria-hidden="true"><FiTruck /></div>
                  <div>
                    <h3>Aucun camion à afficher</h3>
                    <p>Ajoutez votre premier véhicule pour préparer les collectes et suivre sa disponibilité.</p>
                    <Link to="/camions" className="dashboard__text-link">Ajouter un camion <FiArrowRight aria-hidden="true" /></Link>
                  </div>
                </div>
              )}
              <div className="dashboard__panel-footer"><FiTruck aria-hidden="true" /> Véhicules actifs uniquement</div>
            </section>

            <section className="dashboard__execution" aria-labelledby="execution-title">
              <div className="dashboard__execution-top">
                <span className="dashboard__eyebrow">Sur le terrain</span>
                <span className="dashboard__execution-icon"><FiMap aria-hidden="true" /></span>
              </div>
              <h2 id="execution-title">Chaque collecte, étape par étape.</h2>
              <p>{stats.tourneesEnCours > 0
                ? `${stats.tourneesEnCours} tournée${stats.tourneesEnCours > 1 ? 's sont' : ' est'} en cours. Retrouvez l’avancement des points et les preuves de passage.`
                : 'Aucune tournée en cours. Préparez un itinéraire, affectez votre équipe et lancez la prochaine collecte.'}</p>
              <div className="dashboard__execution-features">
                <span><FiMapPin aria-hidden="true" /> Parcours & étapes</span>
                <span><FiClock aria-hidden="true" /> Durées enregistrées</span>
                <span><FiCheckCircle aria-hidden="true" /> Preuves de collecte</span>
              </div>
              <Link to="/tournees" className="dashboard__execution-link">Voir les tournées <FiArrowRight aria-hidden="true" /></Link>
            </section>
          </div>

          <section className="dashboard__shortcuts" aria-labelledby="shortcuts-title">
            <div className="dashboard__shortcuts-heading"><h2 id="shortcuts-title">Préparer vos opérations</h2><span>Vos accès directs</span></div>
            <div className="dashboard__shortcut-grid">
              <Link to="/points" className="dashboard__shortcut"><span className="dashboard__shortcut-icon"><FiMapPin aria-hidden="true" /></span><span><strong>Points de collecte</strong><small>Organiser les lieux de passage</small></span><FiArrowRight aria-hidden="true" /></Link>
              <Link to="/agents" className="dashboard__shortcut"><span className="dashboard__shortcut-icon"><FiUsers aria-hidden="true" /></span><span><strong>Équipe de chauffeurs</strong><small>Gérer les agents et leurs accès</small></span><FiArrowRight aria-hidden="true" /></Link>
              <Link to="/documents-ia" className="dashboard__shortcut"><span className="dashboard__shortcut-icon"><FiFileText aria-hidden="true" /></span><span><strong>Documents de référence</strong><small>Alimenter l’assistant des chauffeurs</small></span><FiArrowRight aria-hidden="true" /></Link>
            </div>
          </section>
        </>
      ) : null}
    </div>
  );
};

export default DashboardPage;
