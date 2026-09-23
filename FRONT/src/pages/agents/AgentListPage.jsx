import { useState, useEffect } from 'react';
import { FiPlus, FiEdit2, FiTrash2, FiSearch, FiCheck, FiX, FiUsers } from 'react-icons/fi';
import toast from 'react-hot-toast';
import { agentService } from '../../services/agentService';
import AgentFormModal from './AgentFormModal';

const AgentListPage = () => {
  const [agents, setAgents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  
  // Modal state
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedAgent, setSelectedAgent] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Delete confirmation modal
  const [deleteModal, setDeleteModal] = useState({ open: false, agent: null });

  useEffect(() => {
    fetchAgents();
  }, []);

  const fetchAgents = async () => {
    try {
      setLoading(true);
      const data = await agentService.getAll();
      setAgents(data);
    } catch (error) {
      console.error(error);
      toast.error('Erreur lors du chargement des agents');
    } finally {
      setLoading(false);
    }
  };

  const handleOpenModal = (agent = null) => {
    setSelectedAgent(agent);
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setSelectedAgent(null);
    setIsModalOpen(false);
  };

  const handleSubmit = async (formData) => {
    try {
      setIsSubmitting(true);
      if (selectedAgent) {
        await agentService.update(selectedAgent.id, formData);
        toast.success('Agent mis à jour avec succès');
      } else {
        await agentService.create(formData);
        toast.success('Chauffeur créé. Son matricule est disponible dans la liste.');
      }
      handleCloseModal();
      fetchAgents();
    } catch (error) {
      console.error(error);
      toast.error(error.response?.data?.message || 'Erreur lors de l\'enregistrement');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDelete = (agent) => {
    setDeleteModal({ open: true, agent });
  };

  const handleDeactivate = async () => {
    try {
      await agentService.delete(deleteModal.agent.id);
      toast.success(`${deleteModal.agent.prenom} ${deleteModal.agent.nom} a été désactivé`);
      setDeleteModal({ open: false, agent: null });
      fetchAgents();
    } catch {
      toast.error('Erreur lors de la désactivation');
    }
  };

  const handleDeletePermanently = async () => {
    try {
      await agentService.deletePermanently(deleteModal.agent.id);
      toast.success(`${deleteModal.agent.prenom} ${deleteModal.agent.nom} a été supprimé définitivement`);
      setDeleteModal({ open: false, agent: null });
      fetchAgents();
    } catch {
      toast.error('Erreur lors de la suppression');
    }
  };

  const filteredAgents = agents.filter(agent => 
    (agent.nom || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
    (agent.prenom || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
    (agent.cin || '').toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <div className="agent-page">
      <div className="page-header flex-between">
        <div>
          <h1>Chauffeurs</h1>
          <p>Gérez les chauffeurs affectés aux tournées de collecte.</p>
        </div>
        <button className="btn-primary" onClick={() => handleOpenModal()}>
          <FiPlus aria-hidden="true" /> Ajouter un chauffeur
        </button>
      </div>

      <div className="card">
        <div className="table-toolbar">
          <div className="search-box">
            <FiSearch className="search-icon" />
            <input 
              type="text" 
              placeholder="Rechercher par nom, prénom, CIN..." 
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>
        </div>

        <div className="table-container">
          {loading ? (
            <div className="loading-state">Chargement des données...</div>
          ) : filteredAgents.length === 0 ? (
            <div className="empty-state">
              <FiUsers aria-hidden="true" />
              <h3>{searchTerm ? 'Aucun chauffeur correspondant' : 'Aucun chauffeur pour le moment'}</h3>
              <p>{searchTerm ? 'Essayez un autre nom, prénom ou numéro de CIN.' : 'Ajoutez un chauffeur pour lui affecter une tournée.'}</p>
            </div>
          ) : (
            <table className="data-table">
              <thead>
                <tr>
                  <th>Chauffeur</th>
                  <th>CIN</th>
                  <th>Matricule</th>
                  <th>Email</th>
                  <th>Contact</th>
                  <th>Permis</th>
                  <th>Statut</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredAgents.map(agent => (
                  <tr key={agent.id}>
                    <td>
                      <div className="user-cell">
                        <div className="avatar-sm">{(agent.prenom || '?').charAt(0)}{(agent.nom || '?').charAt(0)}</div>
                        <span>{agent.prenom} {agent.nom}</span>
                      </div>
                    </td>
                    <td>{agent.cin}</td>
                    <td><span className="status-badge" style={{background: '#f1f5f9', color: '#0369a1', fontWeight: 'bold'}}>{agent.matricule || '-'}</span></td>
                    <td>{agent.email}</td>
                    <td>{agent.telephone}</td>
                    <td>{agent.typePermis ? `Type ${agent.typePermis}` : '-'}</td>
                    <td>
                      <span className={`status-badge ${agent.actif ? 'active' : 'inactive'}`}>
                        {agent.actif ? <><FiCheck /> Actif</> : <><FiX /> Inactif</>}
                      </span>
                    </td>
                    <td className="actions-cell">
                      <button className="icon-btn edit" onClick={() => handleOpenModal(agent)} title="Modifier">
                        <FiEdit2 />
                      </button>
                      <button className="icon-btn delete" onClick={() => handleDelete(agent)} title="Supprimer">
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

      <AgentFormModal 
        isOpen={isModalOpen} 
        onClose={handleCloseModal} 
        onSubmit={handleSubmit} 
        agent={selectedAgent} 
        isLoading={isSubmitting} 
      />

      {/* Delete Confirmation Modal */}
      {deleteModal.open && (
        <div className="delete-modal-overlay" onClick={() => setDeleteModal({ open: false, agent: null })}>
          <div className="delete-modal" onClick={e => e.stopPropagation()}>
            <div className="delete-modal-icon">⚠️</div>
            <h3>Supprimer l'agent</h3>
            <p>Que souhaitez-vous faire avec <strong>{deleteModal.agent?.prenom} {deleteModal.agent?.nom}</strong> ?</p>
            <div className="delete-modal-options">
              <button className="delete-option-deactivate" onClick={handleDeactivate}>
                <span className="option-icon">🔕</span>
                <div>
                  <strong>Désactiver</strong>
                  <small>L'agent reste dans la base de données mais ne peut plus se connecter.</small>
                </div>
              </button>
              <button className="delete-option-permanent" onClick={handleDeletePermanently}>
                <span className="option-icon">🗑️</span>
                <div>
                  <strong>Supprimer définitivement</strong>
                  <small>L'agent sera effacé de la base de données. Cette action est irréversible.</small>
                </div>
              </button>
            </div>
            <button className="delete-modal-cancel" onClick={() => setDeleteModal({ open: false, agent: null })}>Annuler</button>
          </div>
        </div>
      )}

      <style>{`
        .flex-between {
          display: flex;
          justify-content: space-between;
          align-items: center;
        }
        .card {
          background: var(--bg-card);
          border-radius: var(--radius-lg);
          box-shadow: var(--shadow-sm);
          overflow: hidden;
        }
        .table-toolbar {
          padding: 1.5rem;
          border-bottom: 1px solid var(--border-color);
        }
        .search-box {
          position: relative;
          max-width: 400px;
        }
        .search-icon {
          position: absolute;
          left: 12px;
          top: 50%;
          transform: translateY(-50%);
          color: var(--text-muted);
        }
        .search-box input {
          width: 100%;
          padding: 0.6rem 1rem 0.6rem 2.5rem;
          border: 1px solid var(--border-color);
          border-radius: var(--radius-md);
        }
        .table-container {
          overflow-x: auto;
        }
        .data-table {
          width: 100%;
          border-collapse: collapse;
          text-align: left;
        }
        .data-table th, .data-table td {
          padding: 1rem 1.5rem;
          border-bottom: 1px solid var(--border-color);
        }
        .data-table th {
          font-weight: 600;
          color: var(--text-muted);
          background-color: #f8fafc;
        }
        .user-cell {
          display: flex;
          align-items: center;
          gap: 12px;
          font-weight: 500;
        }
        .avatar-sm {
          width: 32px;
          height: 32px;
          background: var(--secondary);
          color: white;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 12px;
        }
        .status-badge {
          display: inline-flex;
          align-items: center;
          gap: 4px;
          padding: 4px 8px;
          border-radius: 12px;
          font-size: 12px;
          font-weight: 600;
        }
        .status-badge.active {
          background: #dcfce7;
          color: #166534;
        }
        .status-badge.inactive {
          background: #fee2e2;
          color: #991b1b;
        }
        .actions-cell {
          display: flex;
          gap: 8px;
        }
        .icon-btn {
          padding: 6px;
          border-radius: 6px;
          display: flex;
          align-items: center;
          justify-content: center;
          color: var(--text-muted);
        }
        .icon-btn:hover { background: #f1f5f9; color: var(--primary); }
        .icon-btn.delete:hover:not(:disabled) { background: #fee2e2; color: #ef4444; }
        .icon-btn:disabled { opacity: 0.3; cursor: not-allowed; }
        .loading-state, .empty-state {
          padding: 3rem;
          text-align: center;
          color: var(--text-muted);
        }
        /* Delete Modal */
        .delete-modal-overlay {
          position: fixed; inset: 0;
          background: rgba(0,0,0,0.5);
          display: flex; align-items: center; justify-content: center;
          z-index: 9999;
          backdrop-filter: blur(4px);
          animation: fadeIn 0.2s ease;
        }
        @keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
        @keyframes slideUp { from { transform: translateY(20px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }
        .delete-modal {
          background: white;
          border-radius: 16px;
          padding: 2rem;
          width: 90%;
          max-width: 460px;
          box-shadow: 0 20px 60px rgba(0,0,0,0.2);
          animation: slideUp 0.25s ease;
          text-align: center;
        }
        .delete-modal-icon { font-size: 3rem; margin-bottom: 0.5rem; }
        .delete-modal h3 { font-size: 1.25rem; font-weight: 700; margin: 0 0 0.5rem; color: #1e293b; }
        .delete-modal p { color: #64748b; margin-bottom: 1.5rem; }
        .delete-modal-options { display: flex; flex-direction: column; gap: 0.75rem; margin-bottom: 1rem; }
        .delete-option-deactivate, .delete-option-permanent {
          display: flex; align-items: center; gap: 1rem;
          padding: 1rem 1.25rem;
          border-radius: 12px;
          border: 2px solid transparent;
          cursor: pointer;
          text-align: left;
          transition: all 0.2s ease;
        }
        .delete-option-deactivate {
          background: #fffbeb;
          border-color: #fde68a;
        }
        .delete-option-deactivate:hover { background: #fef3c7; border-color: #f59e0b; }
        .delete-option-permanent {
          background: #fff1f2;
          border-color: #fecdd3;
        }
        .delete-option-permanent:hover { background: #fee2e2; border-color: #ef4444; }
        .option-icon { font-size: 1.5rem; flex-shrink: 0; }
        .delete-option-deactivate strong, .delete-option-permanent strong { display: block; font-weight: 600; margin-bottom: 2px; }
        .delete-option-deactivate strong { color: #92400e; }
        .delete-option-permanent strong { color: #9f1239; }
        .delete-option-deactivate small, .delete-option-permanent small { color: #6b7280; font-size: 0.78rem; }
        .delete-modal-cancel {
          width: 100%; padding: 0.75rem;
          border-radius: 10px; border: 1px solid #e2e8f0;
          background: #f8fafc; color: #64748b;
          font-weight: 500; cursor: pointer;
          transition: background 0.2s;
        }
        .delete-modal-cancel:hover { background: #e2e8f0; }
      `}</style>
    </div>
  );
};

export default AgentListPage;
