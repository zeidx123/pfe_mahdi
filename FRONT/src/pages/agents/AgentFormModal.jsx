import { useState, useEffect, useRef } from 'react';
import { FiX } from 'react-icons/fi';
import toast from 'react-hot-toast';

const AgentFormModal = ({ isOpen, onClose, onSubmit, agent = null, isLoading = false }) => {
  const firstInputRef = useRef(null);
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

  useEffect(() => {
    if (!isOpen) return;
    const previousFocus = document.activeElement;
    firstInputRef.current?.focus();
    return () => previousFocus?.focus();
  }, [isOpen]);

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

  const handleDialogKeyDown = (event) => {
    if (event.key === 'Escape' && !isLoading) onClose();
    if (event.key !== 'Tab') return;
    const controls = [...event.currentTarget.querySelectorAll('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), a[href]')];
    const first = controls[0];
    const last = controls[controls.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last?.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first?.focus();
    }
  };

  return (
    <div className="modal-overlay">
      <div className="modal-content entity-form-modal" role="dialog" aria-modal="true" aria-labelledby="agent-form-title" aria-describedby="agent-form-description" onKeyDown={handleDialogKeyDown}>
        <div className="modal-header">
          <div>
            <h2 id="agent-form-title">{agent ? 'Modifier le chauffeur' : 'Ajouter un chauffeur'}</h2>
            <p id="agent-form-description" className="modal-subtitle">Renseignez son identité et ses informations professionnelles. Les champs marqués * sont obligatoires.</p>
          </div>
          <button type="button" className="close-btn" onClick={onClose} disabled={isLoading} aria-label="Fermer le formulaire"><FiX aria-hidden="true" /></button>
        </div>
        
        <form onSubmit={handleSubmit}>
          <div className="modal-body">
            <section className="form-section" aria-labelledby="agent-identity-title">
              <h3 id="agent-identity-title" className="form-section-title">Identité et coordonnées</h3>
              <div className="form-grid">
            <div className="form-group">
              <label htmlFor="agent-nom">Nom *</label>
              <input ref={firstInputRef} id="agent-nom" type="text" name="nom" autoComplete="family-name" value={formData.nom} onChange={handleChange} required />
            </div>
            <div className="form-group">
              <label htmlFor="agent-prenom">Prénom *</label>
              <input id="agent-prenom" type="text" name="prenom" autoComplete="given-name" value={formData.prenom} onChange={handleChange} required />
            </div>
            
            <div className="form-group">
              <label htmlFor="agent-cin">Numéro de CIN *</label>
              <input id="agent-cin" type="text" name="cin" value={formData.cin} onChange={handleChange} required />
            </div>
            <div className="form-group">
              <label htmlFor="agent-email">Adresse e-mail *</label>
              <input
                id="agent-email"
                type="email"
                name="email"
                autoComplete="email"
                value={formData.email}
                onChange={handleChange}
                required
                placeholder="prenom.nom@exemple.tn"
              />
            </div>
            
            <div className="form-group">
              <label htmlFor="agent-telephone">Téléphone *</label>
              <input id="agent-telephone" type="tel" name="telephone" autoComplete="tel" value={formData.telephone} onChange={handleChange} required />
            </div>
            <div className="form-group">
              <label htmlFor="agent-adresse">Adresse</label>
              <input id="agent-adresse" type="text" name="adresse" autoComplete="street-address" value={formData.adresse} onChange={handleChange} />
            </div>
              </div>
            </section>
            <section className="form-section" aria-labelledby="agent-professional-title">
              <h3 id="agent-professional-title" className="form-section-title">Permis et affectation</h3>
              <div className="form-grid">
            <div className="form-group">
              <label htmlFor="agent-permis">Numéro de permis</label>
              <input id="agent-permis" type="text" name="numeroPermis" value={formData.numeroPermis} onChange={handleChange} />
            </div>
            <div className="form-group">
              <label htmlFor="agent-type-permis">Catégorie de permis</label>
              <select id="agent-type-permis" name="typePermis" value={formData.typePermis} onChange={handleChange} className="form-select">
                <option value="">Choisir une catégorie</option>
                <option value="B">Permis B (Léger)</option>
                <option value="C">Permis C (Lourd)</option>
                <option value="E">Permis E (Remorque)</option>
              </select>
            </div>
            
            <div className="form-group">
              <label htmlFor="agent-permis-expiration">Expiration du permis</label>
              <input id="agent-permis-expiration" type="date" name="dateExpirationPermis" value={formData.dateExpirationPermis} onChange={handleChange} />
            </div>
            <div className="form-group">
              <label htmlFor="agent-embauche">Date d'embauche *</label>
              <input id="agent-embauche" type="date" name="dateEmbauche" value={formData.dateEmbauche} onChange={handleChange} required />
            </div>
              </div>
            </section>
            <div className="checkbox-field">
              <input type="checkbox" id="agent-actif" name="actif" checked={formData.actif} onChange={handleChange} aria-describedby="agent-actif-help" />
              <div>
                <label htmlFor="agent-actif">Chauffeur actif</label>
                <p id="agent-actif-help" className="field-help">Autorise l'accès à l'application et l'affectation aux tournées.</p>
              </div>
            </div>
          </div>
          
          <div className="modal-footer">
            <button type="button" className="btn-secondary" onClick={onClose} disabled={isLoading}>Annuler</button>
            <button type="submit" className="btn-primary" disabled={isLoading}>
              {isLoading ? 'Enregistrement…' : agent ? 'Enregistrer les modifications' : 'Créer le chauffeur'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default AgentFormModal;
