import { useState, useEffect } from 'react';
import { FiPlus, FiEdit2, FiTrash2, FiSearch, FiTruck, FiAlertCircle } from 'react-icons/fi';
import toast from 'react-hot-toast';
import { camionService } from '../../services/camionService';
import CamionFormModal from './CamionFormModal';

const CamionListPage = () => {
  const [camions, setCamions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterStatut, setFilterStatut] = useState('ALL');
  
  // Modal state
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedCamion, setSelectedCamion] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    fetchCamions();
  }, []);

  const fetchCamions = async () => {
    try {
      setLoading(true);
      const data = await camionService.getAll();
      setCamions(data);
    } catch (error) {
      console.error(error);
      toast.error('Erreur lors du chargement des camions');
    } finally {
      setLoading(false);
    }
  };

  const handleOpenModal = (camion = null) => {
    setSelectedCamion(camion);
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setSelectedCamion(null);
    setIsModalOpen(false);
  };

  const handleSubmit = async (formData) => {
    try {
      setIsSubmitting(true);
      if (selectedCamion) {
        await camionService.update(selectedCamion.id, formData);
        toast.success('Camion mis à jour avec succès');
      } else {
        await camionService.create(formData);
        toast.success('Camion ajouté avec succès');
      }
      handleCloseModal();
      fetchCamions();
    } catch (error) {
      console.error(error);
      toast.error(error.response?.data?.message || 'Erreur lors de l\'enregistrement');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDelete = async (id) => {
    if (window.confirm('Voulez-vous vraiment désactiver ce camion ?')) {
      try {
        await camionService.delete(id);
        toast.success('Camion désactivé');
        fetchCamions();
      } catch (error) {
        toast.error('Erreur lors de la suppression');
      }
    }
  };

  const filteredCamions = camions.filter(camion => {
    const matchesSearch = camion.matricule.toLowerCase().includes(searchTerm.toLowerCase()) || 
                          camion.marque.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesStatut = filterStatut === 'ALL' || camion.statut === filterStatut;
    
    return matchesSearch && matchesStatut;
  });

  const getStatutBadgeClass = (statut) => {
    switch (statut) {
      case 'DISPONIBLE': return 'badge-success';
      case 'EN_TOURNEE': return 'badge-primary';
      case 'EN_MAINTENANCE': return 'badge-warning';
      default: return 'badge-default';
    }
  };

  const getEtatBadgeClass = (etat) => {
    switch (etat) {
      case 'BON': return 'badge-success';
      case 'MOYEN': return 'badge-warning';
      case 'CRITIQUE': return 'badge-danger';
      default: return 'badge-default';
    }
  };

  return (
    <div className="camion-page">
      <div className="page-header flex-between">
        <div>
          <h1>Parc de Camions</h1>
          <p>Gérez les véhicules, leur état et leur disponibilité.</p>
        </div>
        <button className="btn-primary" onClick={() => handleOpenModal()}>
          <FiPlus style={{marginRight: '8px'}} /> Nouveau Camion
        </button>
      </div>

      <div className="card">
        <div className="table-toolbar">
          <div className="search-box">
            <FiSearch className="search-icon" />
            <input 
              type="text" 
              placeholder="Rechercher par matricule, marque..." 
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>
          
          <div className="filter-group">
            <select 
              value={filterStatut} 
              onChange={(e) => setFilterStatut(e.target.value)}
              className="form-select filter-select"
            >
              <option value="ALL">Tous les statuts</option>
              <option value="DISPONIBLE">Disponible</option>
              <option value="EN_TOURNEE">En tournée</option>
              <option value="EN_MAINTENANCE">En maintenance</option>
            </select>
          </div>
        </div>

        <div className="table-container">
          {loading ? (
            <div className="loading-state">Chargement des données...</div>
          ) : filteredCamions.length === 0 ? (
            <div className="empty-state">Aucun camion trouvé.</div>
          ) : (
            <table className="data-table">
              <thead>
                <tr>
                  <th>Matricule</th>
                  <th>Marque & Modèle</th>
                  <th>Type</th>
                  <th>Statut</th>
                  <th>État</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredCamions.map(camion => (
                  <tr key={camion.id} className={!camion.actif ? 'row-inactive' : ''}>
                    <td>
                      <div className="camion-matricule">
                        <FiTruck className="truck-icon" />
                        <span style={{ fontWeight: 600 }}>{camion.matricule}</span>
                      </div>
                    </td>
                    <td>{camion.marque} {camion.modele} ({camion.annee})</td>
                    <td>{camion.typeCamion || '-'}</td>
                    <td>
                      <span className={`status-badge ${getStatutBadgeClass(camion.statut)}`}>
                        {camion.statut.replace('_', ' ')}
                      </span>
                    </td>
                    <td>
                      <span className={`status-badge ${getEtatBadgeClass(camion.etat)}`}>
                        {camion.etat === 'CRITIQUE' && <FiAlertCircle style={{marginRight: '4px'}} />}
                        {camion.etat}
                      </span>
                    </td>
                    <td className="actions-cell">
                      <button className="icon-btn edit" onClick={() => handleOpenModal(camion)} title="Modifier">
                        <FiEdit2 />
                      </button>
                      <button className="icon-btn delete" onClick={() => handleDelete(camion.id)} title="Désactiver" disabled={!camion.actif}>
                        <FiTrash2 />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      <CamionFormModal 
        isOpen={isModalOpen} 
        onClose={handleCloseModal} 
        onSubmit={handleSubmit} 
        camion={selectedCamion} 
        isLoading={isSubmitting} 
      />

      <style>{`
        .flex-between { display: flex; justify-content: space-between; align-items: center; }
        .card { background: var(--bg-card); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); overflow: hidden; }
        .table-toolbar { padding: 1.5rem; border-bottom: 1px solid var(--border-color); display: flex; gap: 1rem; }
        .search-box { position: relative; flex: 1; max-width: 400px; }
        .search-icon { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); color: var(--text-muted); }
        .search-box input { width: 100%; padding: 0.6rem 1rem 0.6rem 2.5rem; border: 1px solid var(--border-color); border-radius: var(--radius-md); }
        .filter-select { padding: 0.6rem 1rem; border-color: var(--border-color); border-radius: var(--radius-md); background: white; }
        .table-container { overflow-x: auto; }
        .data-table { width: 100%; border-collapse: collapse; text-align: left; }
        .data-table th, .data-table td { padding: 1rem 1.5rem; border-bottom: 1px solid var(--border-color); }
        .data-table th { font-weight: 600; color: var(--text-muted); background-color: #f8fafc; }
        .camion-matricule { display: flex; align-items: center; gap: 10px; }
        .truck-icon { color: var(--primary); font-size: 1.2rem; }
        .status-badge { display: inline-flex; align-items: center; padding: 4px 8px; border-radius: 12px; font-size: 12px; font-weight: 600; }
        .badge-success { background: #dcfce7; color: #166534; }
        .badge-primary { background: #dbeafe; color: #1e40af; }
        .badge-warning { background: #fef9c3; color: #854d0e; }
        .badge-danger { background: #fee2e2; color: #991b1b; }
        .badge-default { background: #f1f5f9; color: #475569; }
        .iot-badge { background: #e0e7ff; color: #3730a3; padding: 2px 6px; border-radius: 4px; font-size: 12px; font-family: monospace; }
        .row-inactive td { opacity: 0.5; }
        .actions-cell { display: flex; gap: 8px; }
        .icon-btn { padding: 6px; border-radius: 6px; display: flex; align-items: center; justify-content: center; color: var(--text-muted); border: none; background: transparent; cursor: pointer;}
        .icon-btn:hover { background: #f1f5f9; color: var(--primary); }
        .icon-btn.delete:hover:not(:disabled) { background: #fee2e2; color: #ef4444; }
        .icon-btn:disabled { opacity: 0.3; cursor: not-allowed; }
        .loading-state, .empty-state { padding: 3rem; text-align: center; color: var(--text-muted); }
      `}</style>
    </div>
  );
};

export default CamionListPage;
