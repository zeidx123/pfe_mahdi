import { useState, useEffect } from 'react';
import { FiAlertTriangle, FiCheck, FiInfo, FiSearch } from 'react-icons/fi';
import toast from 'react-hot-toast';
import { alerteService } from '../../services/alerteService';

const AlertePage = () => {
  const [alertes, setAlertes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');

  useEffect(() => {
    fetchAlertes();
  }, []);

  const fetchAlertes = async () => {
    try {
      setLoading(true);
      const data = await alerteService.getAll();
      setAlertes(data);
    } catch {
      toast.error('Erreur lors du chargement des alertes');
    } finally {
      setLoading(false);
    }
  };

  const handleResolve = async (id) => {
    try {
      await alerteService.resolve(id);
      toast.success('Alerte résolue');
      fetchAlertes(); // Recharger
    } catch {
      toast.error('Échec de la résolution de l\'alerte');
    }
  };

  const filtered = alertes.filter(a => 
    a.titre.toLowerCase().includes(searchTerm.toLowerCase()) || 
    a.description.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <div className="alerte-page">
      <div className="page-header">
        <h1>Alertes Système</h1>
        <p>Gérez les anomalies, pannes mécaniques et urgences opérationnelles.</p>
      </div>

      <div className="card">
        <div className="table-toolbar">
          <div className="search-box">
            <FiSearch className="search-icon" />
            <input 
              type="text" 
              placeholder="Rechercher une alerte..." 
              value={searchTerm} 
              onChange={e => setSearchTerm(e.target.value)} 
            />
          </div>
        </div>

        <div className="table-container">
          {loading ? (
            <div className="empty-state">Chargement...</div>
          ) : filtered.length === 0 ? (
            <div className="empty-state">
              <FiCheck size={48} style={{opacity: 0.2, marginBottom: 16, color: 'green'}} />
              <p>Aucune alerte à signaler. Tout fonctionne parfaitement.</p>
            </div>
          ) : (
            <table className="data-table">
              <thead>
                <tr>
                  <th>Niveau</th>
                  <th>Type</th>
                  <th>Source</th>
                  <th>Titre & Description</th>
                  <th>Date</th>
                  <th>Statut</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(a => (
                  <tr key={a.id} className={!a.resolue ? 'unread-row' : ''}>
                    <td>
                      <span className={`badge badge-${a.niveau.toLowerCase()}`}>
                        {a.niveau === 'CRITICAL' ? <FiAlertTriangle /> : <FiInfo />}
                        {a.niveau}
                      </span>
                    </td>
                    <td><span className="badge-outline">{a.type}</span></td>
                    <td><strong>{a.sourceId || 'N/A'}</strong></td>
                    <td>
                      <div style={{fontWeight: !a.resolue ? 600 : 400}}>{a.titre}</div>
                      <div style={{fontSize: '12px', color: 'var(--text-muted)'}}>{a.description}</div>
                    </td>
                    <td>{new Date(a.dateCreation).toLocaleString()}</td>
                    <td>
                      {a.resolue ? (
                        <span className="status-resolved">Résolue le {new Date(a.dateResolution).toLocaleDateString()}</span>
                      ) : (
                        <span className="status-active">Active</span>
                      )}
                    </td>
                    <td>
                      {!a.resolue && (
                        <button className="btn-primary" onClick={() => handleResolve(a.id)}>
                          <FiCheck style={{marginRight: 6}}/> Résoudre
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      <style>{`
        .alerte-page { display: flex; flex-direction: column; gap: 1.5rem; height: 100%; }
        .card { background: var(--bg-card); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); overflow: hidden; }
        .table-toolbar { padding: 1.5rem; border-bottom: 1px solid var(--border-color); display: flex; gap: 1rem; }
        .search-box { position: relative; max-width: 400px; width: 100%; }
        .search-icon { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); color: var(--text-muted); }
        .search-box input { width: 100%; padding: 0.6rem 1rem 0.6rem 2.5rem; border: 1px solid var(--border-color); border-radius: var(--radius-md); }
        .table-container { overflow-x: auto; }
        .data-table { width: 100%; border-collapse: collapse; text-align: left; }
        .data-table th, .data-table td { padding: 1rem 1.25rem; border-bottom: 1px solid var(--border-color); vertical-align: middle; }
        .data-table th { font-weight: 600; color: var(--text-muted); background: #f8fafc; font-size: 13px; text-transform: uppercase; letter-spacing: 0.04em; }
        
        .unread-row { background-color: #fef2f2; } /* slight red tint for unread alerts */
        
        .badge { display: inline-flex; align-items: center; gap: 4px; padding: 4px 8px; border-radius: 12px; font-size: 11px; font-weight: 700; }
        .badge-critical { background: #fee2e2; color: #991b1b; }
        .badge-warning { background: #fef9c3; color: #854d0e; }
        .badge-info { background: #e0f2fe; color: #0369a1; }
        
        .badge-outline { font-size: 11px; font-weight: 600; padding: 2px 6px; border: 1px solid var(--border-color); border-radius: 4px; }
        
        .status-active { color: #dc2626; font-weight: 600; font-size: 13px; }
        .status-resolved { color: #16a34a; font-size: 12px; }
        
        .empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 4rem 2rem; text-align: center; color: var(--text-muted); }
      `}</style>
    </div>
  );
};

export default AlertePage;
