import { useState, useEffect } from 'react';
import { FiFileText, FiDownload, FiSearch } from 'react-icons/fi';
import toast from 'react-hot-toast';
import { tourneeService } from '../../services/tourneeService';
import { rapportService } from '../../services/rapportService';

const RapportPage = () => {
  const [tourneesTerminees, setTourneesTerminees] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [downloadingId, setDownloadingId] = useState(null);

  useEffect(() => {
    fetchTournees();
  }, []);

  const fetchTournees = async () => {
    try {
      setLoading(true);
      const data = await tourneeService.getAll();
      // On ne garde que les tournées terminées pour les rapports
      setTourneesTerminees(data.filter(t => t.statut === 'TERMINEE'));
    } catch {
      toast.error('Erreur lors du chargement de l\'historique');
    } finally {
      setLoading(false);
    }
  };

  const handleDownload = async (id, reference) => {
    try {
      setDownloadingId(id);
      await rapportService.downloadTourneePdf(id, reference);
      toast.success('Téléchargement lancé');
    } catch {
      toast.error('Échec du téléchargement du PDF');
    } finally {
      setDownloadingId(null);
    }
  };

  const filtered = tourneesTerminees.filter(t => 
    t.nom.toLowerCase().includes(searchTerm.toLowerCase()) || 
    t.reference.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <div className="rapport-page">
      <div className="page-header">
        <h1>Rapports de Collecte</h1>
        <p>Générez et téléchargez les rapports PDF des tournées terminées.</p>
      </div>

      <div className="card">
        <div className="table-toolbar">
          <div className="search-box">
            <FiSearch className="search-icon" />
            <input 
              type="text" 
              placeholder="Rechercher une tournée..." 
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
              <FiFileText size={48} style={{opacity: 0.2, marginBottom: 16}} />
              <p>Aucun rapport disponible. (Aucune tournée n'est terminée)</p>
            </div>
          ) : (
            <table className="data-table">
              <thead>
                <tr>
                  <th>Référence</th>
                  <th>Nom de la tournée</th>
                  <th>Date de réalisation</th>
                  <th>Points collectés</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(t => (
                  <tr key={t.id}>
                    <td><strong>{t.reference}</strong></td>
                    <td>{t.nom}</td>
                    <td>{t.heureFinReelle ? new Date(t.heureFinReelle).toLocaleDateString() : t.datePrevue}</td>
                    <td>{t.nombrePoints} points</td>
                    <td>
                      <button 
                        className="btn-primary" 
                        onClick={() => handleDownload(t.id, t.reference)}
                        disabled={downloadingId === t.id}
                      >
                        <FiDownload style={{marginRight: 8}} />
                        {downloadingId === t.id ? 'Génération...' : 'PDF'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      <style>{`
        .rapport-page { display: flex; flex-direction: column; gap: 1.5rem; height: 100%; }
        .card { background: var(--bg-card); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); overflow: hidden; }
        .table-toolbar { padding: 1.5rem; border-bottom: 1px solid var(--border-color); display: flex; gap: 1rem; }
        .search-box { position: relative; max-width: 400px; width: 100%; }
        .search-icon { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); color: var(--text-muted); }
        .search-box input { width: 100%; padding: 0.6rem 1rem 0.6rem 2.5rem; border: 1px solid var(--border-color); border-radius: var(--radius-md); }
        .table-container { overflow-x: auto; }
        .data-table { width: 100%; border-collapse: collapse; text-align: left; }
        .data-table th, .data-table td { padding: 1rem 1.25rem; border-bottom: 1px solid var(--border-color); vertical-align: middle; }
        .data-table th { font-weight: 600; color: var(--text-muted); background: #f8fafc; font-size: 13px; text-transform: uppercase; letter-spacing: 0.04em; }
        .empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 4rem 2rem; text-align: center; color: var(--text-muted); }
      `}</style>
    </div>
  );
};

export default RapportPage;
