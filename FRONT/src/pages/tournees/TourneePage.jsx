import { useState, useEffect } from 'react';
import { FiPlus, FiEdit2, FiTrash2, FiSearch, FiCalendar, FiTruck, FiUser, FiMapPin, FiPlay, FiCheck, FiX, FiFileText, FiEye } from 'react-icons/fi';
import toast from 'react-hot-toast';
import { tourneeService } from '../../services/tourneeService';
import { rapportService } from '../../services/rapportService';
import TourneeFormModal from './TourneeFormModal';
import TourneeDetailsModal from './TourneeDetailsModal';

const STATUT_CONFIG = {
  PLANIFIEE:   { label: 'Planifiée',   cls: 'badge-info',    icon: <FiCalendar /> },
  EN_COURS:    { label: 'En cours',    cls: 'badge-warning', icon: <FiPlay /> },
  TERMINEE:    { label: 'Terminée',    cls: 'badge-success', icon: <FiCheck /> },
  ANNULEE:     { label: 'Annulée',     cls: 'badge-danger',  icon: <FiX /> },
};

const TourneePage = () => {
  const [tournees, setTournees] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterStatut, setFilterStatut] = useState('ALL');

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedTournee, setSelectedTournee] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const [isDetailsOpen, setIsDetailsOpen] = useState(false);
  const [detailsTournee, setDetailsTournee] = useState(null);

  const handleOpenDetails = async (tournee) => {
    try {
      const current = await tourneeService.getById(tournee.id);
      setDetailsTournee(current);
      setIsDetailsOpen(true);
    } catch (error) { toast.error(error.response?.data?.message || 'Détails indisponibles. Réessayez.'); }
  };

  const handleCloseDetails = () => {
    setDetailsTournee(null);
    setIsDetailsOpen(false);
  };

  useEffect(() => { fetchTournees(); }, []);

  const fetchTournees = async (updated = null) => {
    if (updated?.id) setDetailsTournee(current => current?.id === updated.id ? updated : current);
    try {
      setLoading(true);
      const data = await tourneeService.getAll();
      setTournees(data);
      setDetailsTournee(current => current ? data.find(t => t.id === current.id) || current : null);
    } catch {
      toast.error('Erreur lors du chargement des tournées');
    } finally {
      setLoading(false);
    }
  };

  const handleOpenModal = (tournee = null) => {
    setSelectedTournee(tournee);
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setSelectedTournee(null);
    setIsModalOpen(false);
  };

  const handleSubmit = async (formData) => {
    try {
      setIsSubmitting(true);
      if (selectedTournee) {
        await tourneeService.update(selectedTournee.id, formData);
        toast.success('Tournée mise à jour');
      } else {
        await tourneeService.create(formData);
        toast.success('Tournée créée avec succès');
      }
      handleCloseModal();
      fetchTournees();
    } catch (error) {
      toast.error(error.response?.data?.message || 'Erreur lors de l\'enregistrement');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDelete = async (id) => {
    if (window.confirm('Voulez-vous annuler cette tournée ?')) {
      try {
        await tourneeService.delete(id);
        toast.success('Tournée annulée');
        fetchTournees();
      } catch {
        toast.error('Erreur lors de la suppression');
      }
    }
  };

  const handleDownloadPdf = async (id, reference) => {
    try {
      await rapportService.downloadTourneePdf(id, reference);
      toast.success('Rapport PDF téléchargé');
    } catch {
      toast.error('Erreur lors du téléchargement du PDF');
    }
  };

  const filtered = tournees.filter(t => {
    const matchSearch = (t.nom || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
                        (t.reference || '').toLowerCase().includes(searchTerm.toLowerCase());
    const matchStatut = filterStatut === 'ALL' || t.statut === filterStatut;
    return matchSearch && matchStatut;
  });

  // Stats rapides
  const stats = {
    total: tournees.length,
    planifiees: tournees.filter(t => t.statut === 'PLANIFIEE').length,
    enCours: tournees.filter(t => t.statut === 'EN_COURS').length,
    terminees: tournees.filter(t => t.statut === 'TERMINEE').length,
  };

  return (
    <div className="tournee-page">
      <div className="tournee-header page-header">
        <div>
          <h1>Tournées</h1>
          <p>Planifiez et suivez les circuits de collecte des déchets.</p>
        </div>
        <button className="btn-primary" onClick={() => handleOpenModal()}>
          <FiPlus /> Créer une tournée
        </button>
      </div>

      {/* Stats bar */}
      <div className="stats-bar">
        <div className="stat-chip total"><span>{stats.total}</span> Total</div>
        <div className="stat-chip planifiees"><FiCalendar /><span>{stats.planifiees}</span> Planifiées</div>
        <div className="stat-chip en-cours"><FiPlay /><span>{stats.enCours}</span> En cours</div>
        <div className="stat-chip terminees"><FiCheck /><span>{stats.terminees}</span> Terminées</div>
      </div>

      <div className="card">
        <div className="table-toolbar">
          <div className="search-box">
            <FiSearch className="search-icon" />
            <input type="text" placeholder="Rechercher par nom, référence..." value={searchTerm} onChange={e => setSearchTerm(e.target.value)} />
          </div>
          <select value={filterStatut} onChange={e => setFilterStatut(e.target.value)} className="form-select filter-select">
            <option value="ALL">Tous les statuts</option>
            <option value="PLANIFIEE">Planifiée</option>
            <option value="EN_COURS">En cours</option>
            <option value="TERMINEE">Terminée</option>
            <option value="ANNULEE">Annulée</option>
          </select>
        </div>

        <div className="table-container">
          {loading ? (
            <div className="empty-state">Chargement...</div>
          ) : filtered.length === 0 ? (
            <div className="empty-state">
              <FiMapPin aria-hidden="true" />
              <h3>Aucune tournée trouvée</h3>
              <p>Préparez un circuit en affectant un chauffeur, un camion et des points de collecte.</p>
            </div>
          ) : (
            <table className="data-table">
              <thead>
                <tr>
                  <th>Référence</th>
                  <th>Nom</th>
                  <th>Date prévue</th>
                  <th>Camion</th>
                  <th>Chauffeur</th>
                  <th>Points</th>
                  <th>Statut</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(t => {
                  const cfg = STATUT_CONFIG[t.statut] || STATUT_CONFIG.PLANIFIEE;
                  // Review priority from the local vision engine; it has no effect on the tour status.
                  const toReview = (t.visits || []).filter(v => v.analysisDecision === 'A_VERIFIER').length;
                  return (
                    <tr key={t.id}>
                      <td><strong style={{fontFamily:'monospace'}}>{t.reference}</strong></td>
                      <td>{t.nom}</td>
                      <td>
                        <div style={{display:'flex',alignItems:'center',gap:6}}>
                          <FiCalendar style={{color:'var(--text-muted)'}} />
                          {t.datePrevue}
                          {t.heureDepartPrevue && <span style={{color:'var(--text-muted)',fontSize:12}}>à {t.heureDepartPrevue}</span>}
                        </div>
                      </td>
                      <td>
                        <div style={{display:'flex',alignItems:'center',gap:6}}>
                          <FiTruck style={{color:'var(--primary)'}} />
                          {t.camionMatricule || '-'}
                        </div>
                      </td>
                      <td>
                        <div style={{display:'flex',alignItems:'center',gap:6}}>
                          <FiUser style={{color:'var(--secondary)'}} />
                          {t.chauffeurNomComplet || '-'}
                        </div>
                      </td>
                      <td>
                        <span style={{display:'flex',alignItems:'center',gap:4}}>
                          <FiMapPin style={{color:'var(--text-muted)'}} />
                          {t.nombrePoints || 0} point(s)
                        </span>
                      </td>
                      <td>
                        <div className="statut-control">
                          <span className={`status-badge ${cfg.cls}`}>{cfg.icon} {cfg.label}</span>
                          {toReview > 0 && <span className="status-badge badge-warning" title="Analyses photo à vérifier par le superviseur">À vérifier : {toReview}</span>}
                        </div>
                      </td>
                      <td className="actions-cell">
                        <button className="icon-btn edit" onClick={() => handleOpenDetails(t)} title="Voir détails"><FiEye /></button>
                        {t.statut === 'TERMINEE' && (
                          <button className="icon-btn edit" style={{color: '#0369a1', background: '#e0f2fe'}} onClick={() => handleDownloadPdf(t.id, t.reference)} title="Télécharger PDF"><FiFileText /></button>
                        )}
                        <button className="icon-btn edit" onClick={() => handleOpenModal(t)} title="Modifier la tournée planifiée" disabled={t.statut !== 'PLANIFIEE'}><FiEdit2 /></button>
                        <button className="icon-btn delete" onClick={() => handleDelete(t.id)} title="Annuler" disabled={t.statut === 'ANNULEE' || t.statut === 'TERMINEE'}><FiTrash2 /></button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>
      </div>

      <TourneeFormModal isOpen={isModalOpen} onClose={handleCloseModal} onSubmit={handleSubmit} tournee={selectedTournee} isLoading={isSubmitting} />
      <TourneeDetailsModal key={detailsTournee?.id || 'none'} isOpen={isDetailsOpen} onClose={handleCloseDetails} tournee={detailsTournee} onRefresh={fetchTournees} />

      <style>{`
        .tournee-page { display: flex; flex-direction: column; gap: 1.5rem; }
        .tournee-header { display: flex; justify-content: space-between; align-items: flex-start; }
        .stats-bar { display: flex; gap: 1rem; flex-wrap: wrap; }
        .stat-chip { display: flex; align-items: center; gap: 8px; padding: 10px 18px; border-radius: 12px; font-size: 14px; font-weight: 500; background: white; box-shadow: var(--shadow-sm); border: 1px solid var(--border-color); }
        .stat-chip span { font-size: 20px; font-weight: 700; }
        .stat-chip.planifiees { border-left: 4px solid #3b82f6; }
        .stat-chip.en-cours { border-left: 4px solid #f59e0b; }
        .stat-chip.terminees { border-left: 4px solid #22c55e; }
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
        .statut-control { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
        .status-badge { display: inline-flex; align-items: center; gap: 4px; padding: 4px 8px; border-radius: 12px; font-size: 12px; font-weight: 600; white-space: nowrap; }
        .badge-info { background: #dbeafe; color: #1e40af; }
        .badge-warning { background: #fef9c3; color: #854d0e; }
        .badge-success { background: #dcfce7; color: #166534; }
        .badge-danger { background: #fee2e2; color: #991b1b; }
        .mini-btn { padding: 3px 8px; border-radius: 6px; font-size: 11px; font-weight: 600; border: none; cursor: pointer; white-space: nowrap; }
        .mini-btn.green { background: #dcfce7; color: #166534; }
        .mini-btn.green:hover { background: #bbf7d0; }
        .mini-btn.blue { background: #dbeafe; color: #1e40af; }
        .mini-btn.blue:hover { background: #bfdbfe; }
        .actions-cell { display: flex; gap: 6px; }
        .icon-btn { padding: 6px; border-radius: 6px; display: flex; align-items: center; justify-content: center; color: var(--text-muted); border: none; background: transparent; cursor: pointer; }
        .icon-btn:hover { background: #f1f5f9; color: var(--primary); }
        .icon-btn.delete:hover:not(:disabled) { background: #fee2e2; color: #ef4444; }
        .icon-btn:disabled { opacity: 0.3; cursor: not-allowed; }
        .empty-state { padding: 3rem; text-align: center; color: var(--text-muted); }
      `}</style>
    </div>
  );
};

export default TourneePage;
