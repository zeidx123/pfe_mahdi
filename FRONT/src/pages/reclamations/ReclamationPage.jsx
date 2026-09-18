import { useState, useEffect } from 'react';
import { FiMessageSquare, FiSearch, FiCheck, FiSend, FiClock, FiAlertTriangle } from 'react-icons/fi';
import toast from 'react-hot-toast';
import { reclamationService } from '../../services/reclamationService';

const ReclamationPage = () => {
  const [reclamations, setReclamations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterStatut, setFilterStatut] = useState('ALL');

  // Modal State
  const [selectedRec, setSelectedRec] = useState(null);
  const [reponseText, setReponseText] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    fetchReclamations();
  }, []);

  const fetchReclamations = async () => {
    try {
      setLoading(true);
      const data = await reclamationService.getAll();
      // Trier du plus récent au plus ancien
      data.sort((a, b) => new Date(b.dateCreation) - new Date(a.dateCreation));
      setReclamations(data);
    } catch {
      toast.error('Erreur lors du chargement des réclamations');
    } finally {
      setLoading(false);
    }
  };

  const handleRepondre = async (e) => {
    e.preventDefault();
    if (!reponseText.trim()) {
      toast.error('Veuillez saisir une réponse');
      return;
    }
    try {
      setSubmitting(true);
      await reclamationService.repondre(selectedRec.id, reponseText);
      toast.success('Réponse envoyée avec succès');
      setSelectedRec(null);
      setReponseText('');
      fetchReclamations();
    } catch {
      toast.error('Erreur lors de l\'envoi de la réponse');
    } finally {
      setSubmitting(false);
    }
  };

  const formatType = (type) => {
    switch (type) {
      case 'PANNE_MECANIQUE':
        return '⚙️ Panne mécanique';
      case 'ROUTE_BLOQUEE':
        return '🚧 Route bloquée';
      case 'BAC_DEGRADE':
        return '🗑️ Bac dégradé / manquant';
      default:
        return '📝 Autre';
    }
  };

  const filtered = reclamations.filter(r => {
    const matchSearch = (r.titre || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
                        r.chauffeurNom.toLowerCase().includes(searchTerm.toLowerCase()) ||
                        (r.description || '').toLowerCase().includes(searchTerm.toLowerCase());
    const matchStatut = filterStatut === 'ALL' || r.statut === filterStatut;
    return matchSearch && matchStatut;
  });

  return (
    <div className="reclamation-page">
      <div className="page-header-row">
        <div>
          <h1>Gestion des Réclamations</h1>
          <p>Consultez et traitez les réclamations et problèmes signalés par les chauffeurs sur le terrain.</p>
        </div>
      </div>

      <div className="card">
        <div className="table-toolbar">
          <div className="search-box">
            <FiSearch className="search-icon" />
            <input 
              type="text" 
              placeholder="Rechercher par titre, chauffeur, description..." 
              value={searchTerm} 
              onChange={e => setSearchTerm(e.target.value)} 
            />
          </div>
          <select 
            value={filterStatut} 
            onChange={e => setFilterStatut(e.target.value)} 
            className="form-select filter-select"
          >
            <option value="ALL">Tous les statuts</option>
            <option value="EN_ATTENTE">En attente</option>
            <option value="TRAITEE">Traitée</option>
          </select>
        </div>

        <div className="table-container">
          {loading ? (
            <div className="empty-state">Chargement des réclamations...</div>
          ) : filtered.length === 0 ? (
            <div className="empty-state">
              <FiMessageSquare size={48} style={{ opacity: 0.2, marginBottom: 16 }} />
              <p>Aucune réclamation trouvée.</p>
            </div>
          ) : (
            <table className="data-table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Chauffeur</th>
                  <th>Type</th>
                  <th>Sujet</th>
                  <th>Description</th>
                  <th>Statut</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(r => {
                  const isTraitee = r.statut === 'TRAITEE';
                  return (
                    <tr key={r.id}>
                      <td style={{ whiteSpace: 'nowrap' }}>
                        {new Date(r.dateCreation).toLocaleDateString()} à {new Date(r.dateCreation).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </td>
                      <td><strong>{r.chauffeurNom}</strong></td>
                      <td style={{ whiteSpace: 'nowrap' }}>{formatType(r.type)}</td>
                      <td><strong>{r.titre}</strong></td>
                      <td className="description-cell" title={r.description}>{r.description}</td>
                      <td>
                        <span className={`status-badge ${isTraitee ? 'badge-success' : 'badge-warning'}`}>
                          {isTraitee ? <FiCheck /> : <FiClock />} {isTraitee ? 'Traitée' : 'En attente'}
                        </span>
                      </td>
                      <td>
                        {!isTraitee ? (
                          <button 
                            className="btn-primary btn-sm"
                            onClick={() => setSelectedRec(r)}
                          >
                            Répondre
                          </button>
                        ) : (
                          <button 
                            className="btn-secondary btn-sm"
                            onClick={() => setSelectedRec(r)}
                            title="Voir la réponse"
                          >
                            Voir réponse
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>
      </div>

      {/* Reply Modal */}
      {selectedRec && (
        <div className="modal-overlay">
          <div className="modal-content modal-md">
            <div className="modal-header">
              <h2>{selectedRec.statut === 'TRAITEE' ? 'Détails de la Réclamation' : 'Répondre à la Réclamation'}</h2>
              <button className="close-btn" onClick={() => { setSelectedRec(null); setReponseText(''); }}>×</button>
            </div>
            <form onSubmit={handleRepondre}>
              <div className="modal-body">
                <div className="rec-details-panel">
                  <p><strong>De :</strong> {selectedRec.chauffeurNom}</p>
                  <p><strong>Type :</strong> {formatType(selectedRec.type)}</p>
                  <p><strong>Sujet :</strong> {selectedRec.titre}</p>
                  <p><strong>Date :</strong> {new Date(selectedRec.dateCreation).toLocaleString()}</p>
                  <div className="rec-description-box">
                    <strong>Description :</strong>
                    <p>{selectedRec.description}</p>
                  </div>
                </div>

                {selectedRec.statut === 'TRAITEE' ? (
                  <div className="rec-reponse-box">
                    <strong>Votre Réponse :</strong>
                    <p className="reponse-content">{selectedRec.reponse}</p>
                    <span className="traitement-date">
                      Traitée le {new Date(selectedRec.dateTraitement).toLocaleString()}
                    </span>
                  </div>
                ) : (
                  <div className="form-group mt-3">
                    <label>Saisir votre réponse au chauffeur *</label>
                    <textarea
                      className="form-control"
                      rows="4"
                      required
                      placeholder="Ex: Le camion de rechange est en route. / Le point a été marqué pour inspection..."
                      value={reponseText}
                      onChange={e => setReponseText(e.target.value)}
                    ></textarea>
                  </div>
                )}
              </div>
              <div className="modal-footer">
                <button 
                  type="button" 
                  className="btn-secondary" 
                  onClick={() => { setSelectedRec(null); setReponseText(''); }}
                >
                  Fermer
                </button>
                {selectedRec.statut !== 'TRAITEE' && (
                  <button 
                    type="submit" 
                    className="btn-primary" 
                    disabled={submitting}
                  >
                    <FiSend style={{ marginRight: 6 }} />
                    {submitting ? 'Envoi...' : 'Envoyer la réponse'}
                  </button>
                )}
              </div>
            </form>
          </div>
        </div>
      )}

      <style>{`
        .reclamation-page { display: flex; flex-direction: column; gap: 1.5rem; }
        .page-header-row { display: flex; justify-content: space-between; align-items: flex-start; }
        .card { background: var(--bg-card); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); overflow: hidden; }
        .table-toolbar { padding: 1.5rem; border-bottom: 1px solid var(--border-color); display: flex; gap: 1rem; }
        .search-box { position: relative; flex: 1; max-width: 400px; }
        .search-icon { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); color: var(--text-muted); }
        .search-box input { width: 100%; padding: 0.6rem 1rem 0.6rem 2.5rem; border: 1px solid var(--border-color); border-radius: var(--radius-md); }
        .filter-select { padding: 0.6rem 1rem; border: 1px solid var(--border-color); border-radius: var(--radius-md); background: white; }
        .table-container { overflow-x: auto; }
        .data-table { width: 100%; border-collapse: collapse; text-align: left; }
        .data-table th, .data-table td { padding: 0.9rem 1.25rem; border-bottom: 1px solid var(--border-color); vertical-align: middle; }
        .data-table th { font-weight: 600; color: var(--text-muted); background: #f8fafc; font-size: 13px; text-transform: uppercase; letter-spacing: 0.04em; }
        .description-cell { max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
        .status-badge { display: inline-flex; align-items: center; gap: 4px; padding: 4px 8px; border-radius: 12px; font-size: 11px; font-weight: 600; }
        .badge-success { background: #dcfce7; color: #166534; }
        .badge-warning { background: #fef9c3; color: #854d0e; }
        .btn-sm { padding: 4px 8px; font-size: 12px; border-radius: 6px; }
        
        .rec-details-panel { background: #f8fafc; padding: 12px; border-radius: 8px; border: 1px solid #e2e8f0; font-size: 13px; }
        .rec-details-panel p { margin: 4px 0; }
        .rec-description-box { margin-top: 10px; padding-top: 10px; border-top: 1px dashed #cbd5e1; }
        .rec-description-box p { background: white; padding: 8px; border-radius: 6px; border: 1px solid #e2e8f0; font-style: italic; margin-top: 4px; }
        
        .rec-reponse-box { margin-top: 15px; padding: 12px; background: #f0fdf4; border-left: 4px solid #22c55e; border-radius: 4px 8px 8px 4px; font-size: 13px; }
        .reponse-content { font-style: italic; background: white; padding: 8px; border-radius: 6px; border: 1px solid #bbf7d0; margin: 4px 0; }
        .traitement-date { font-size: 10px; color: #15803d; display: block; text-align: right; }
        .form-group textarea { width: 100%; border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 8px; font-size: 13px; resize: none; }
        .mt-3 { margin-top: 1rem; }
      `}</style>
    </div>
  );
};

export default ReclamationPage;
