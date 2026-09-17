import { useState, useEffect } from 'react';
import { FiX } from 'react-icons/fi';
import toast from 'react-hot-toast';

const AgentFormModal = ({ isOpen, onClose, onSubmit, agent = null, isLoading = false }) => {
  const [formData, setFormData] = useState({
    nom: '',
    prenom: '',
    cin: '',
    email: '',
    telephone: '',
    adresse: '',
    numeroPermis: '',
    dateExpirationPermis: '',
    typePermis: '',
    dateEmbauche: '',
    actif: true
  });

  useEffect(() => {
    if (agent) {
      setFormData({
        nom: agent.nom || '',
        prenom: agent.prenom || '',
        cin: agent.cin || '',
        email: agent.email || '',
        telephone: agent.telephone || '',
        adresse: agent.adresse || '',
        numeroPermis: agent.numeroPermis || '',
        dateExpirationPermis: agent.dateExpirationPermis || '',
        typePermis: agent.typePermis || '',
        dateEmbauche: agent.dateEmbauche || '',
        actif: agent.actif !== undefined ? agent.actif : true
      });
    } else {
      setFormData({
        nom: '', prenom: '', cin: '', email: '', telephone: '', adresse: '',
        numeroPermis: '', dateExpirationPermis: '', typePermis: '', dateEmbauche: '', actif: true
      });
    }
  }, [agent, isOpen]);

  if (!isOpen) return null;

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData({
      ...formData,
      [name]: type === 'checkbox' ? checked : value
    });
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!formData.nom || !formData.prenom || !formData.cin || !formData.email || !formData.telephone || !formData.dateEmbauche) {
      toast.error('Veuillez remplir tous les champs obligatoires');
      return;
    }
    onSubmit(formData);
  };

  return (
    <div className="modal-overlay">
      <div className="modal-content">
        <div className="modal-header">
          <h2>{agent ? 'Modifier l\'agent' : 'Nouvel agent'}</h2>
          <button className="close-btn" onClick={onClose}><FiX /></button>
        </div>
        
        <form onSubmit={handleSubmit}>
          <div className="modal-body form-grid">
            <div className="form-group">
              <label>Nom *</label>
              <input type="text" name="nom" value={formData.nom} onChange={handleChange} required />
            </div>
            <div className="form-group">
              <label>Prénom *</label>
              <input type="text" name="prenom" value={formData.prenom} onChange={handleChange} required />
            </div>
            
            <div className="form-group">
              <label>CIN *</label>
              <input type="text" name="cin" value={formData.cin} onChange={handleChange} required />
            </div>
            <div className="form-group">
              <label>Email Personnel (pour recevoir l'identifiant) *</label>
              <input
                type="email"
                name="email"
                value={formData.email}
                onChange={handleChange}
                required
                placeholder="ex: jean.dupont@gmail.com"
              />
            </div>
            
            <div className="form-group">
              <label>Téléphone *</label>
              <input type="text" name="telephone" value={formData.telephone} onChange={handleChange} required />
            </div>
            <div className="form-group">
              <label>Adresse</label>
              <input type="text" name="adresse" value={formData.adresse} onChange={handleChange} />
            </div>
            
            <div className="form-group">
              <label>N° Permis</label>
              <input type="text" name="numeroPermis" value={formData.numeroPermis} onChange={handleChange} />
            </div>
            <div className="form-group">
              <label>Type de permis</label>
              <select name="typePermis" value={formData.typePermis} onChange={handleChange} className="form-select">
                <option value="">Sélectionner</option>
                <option value="B">Permis B (Léger)</option>
                <option value="C">Permis C (Lourd)</option>
                <option value="E">Permis E (Remorque)</option>
              </select>
            </div>
            
            <div className="form-group">
              <label>Expiration du permis</label>
              <input type="date" name="dateExpirationPermis" value={formData.dateExpirationPermis} onChange={handleChange} />
            </div>
            <div className="form-group">
              <label>Date d'embauche *</label>
              <input type="date" name="dateEmbauche" value={formData.dateEmbauche} onChange={handleChange} required />
            </div>
            
            <div className="form-group full-width" style={{display: 'flex', alignItems: 'center', gap: '10px'}}>
              <input type="checkbox" id="actif" name="actif" checked={formData.actif} onChange={handleChange} style={{width: 'auto'}} />
              <label htmlFor="actif" style={{marginBottom: 0}}>Agent actif (Autorisé à se connecter et à conduire)</label>
            </div>
          </div>
          
          <div className="modal-footer">
            <button type="button" className="btn-secondary" onClick={onClose} disabled={isLoading}>Annuler</button>
            <button type="submit" className="btn-primary" disabled={isLoading}>
              {isLoading ? 'Enregistrement...' : 'Enregistrer'}
            </button>
          </div>
        </form>
      </div>
      
      <style>{`
        .modal-overlay {
          position: fixed;
          top: 0; left: 0; right: 0; bottom: 0;
          background: rgba(0,0,0,0.5);
          display: flex;
          align-items: center;
          justify-content: center;
          z-index: 1000;
        }
        .modal-content {
          background: var(--bg-card);
          border-radius: var(--radius-lg);
          width: 90%;
          max-width: 650px;
          max-height: 90vh;
          overflow-y: auto;
          box-shadow: var(--shadow-lg);
        }
        .modal-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          padding: 1.5rem;
          border-bottom: 1px solid var(--border-color);
        }
        .modal-header h2 { font-size: 1.25rem; font-weight: 600; }
        .close-btn { font-size: 1.5rem; color: var(--text-muted); }
        .close-btn:hover { color: var(--text-main); }
        .modal-body { padding: 1.5rem; }
        .form-grid {
          display: grid;
          grid-template-columns: 1fr 1fr;
          gap: 1rem;
        }
        .full-width { grid-column: 1 / -1; }
        .form-select {
          width: 100%;
          padding: 0.75rem;
          border: 1px solid var(--border-color);
          border-radius: var(--radius-md);
          background: white;
          font-family: inherit;
        }
        .modal-footer {
          padding: 1.5rem;
          border-top: 1px solid var(--border-color);
          display: flex;
          justify-content: flex-end;
          gap: 1rem;
        }
        .btn-secondary {
          padding: 0.75rem 1.5rem;
          border-radius: var(--radius-md);
          background: #f1f5f9;
          color: #475569;
          font-weight: 500;
        }
        .btn-secondary:hover { background: #e2e8f0; }
      `}</style>
    </div>
  );
};

export default AgentFormModal;
