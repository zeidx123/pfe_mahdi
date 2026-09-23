import { useState, useEffect, useRef } from 'react';
import { FiX } from 'react-icons/fi';
import toast from 'react-hot-toast';

const CamionFormModal = ({ isOpen, onClose, onSubmit, camion = null, isLoading = false }) => {
  const firstInputRef = useRef(null);
  const [formData, setFormData] = useState({
    matricule: '',
    marque: '',
    modele: '',
    annee: '',
    typeCamion: '',
    capacitePoids: '',
    capaciteReservoir: '',
    kilometrage: '',
    dateMiseEnService: '',
    statut: 'DISPONIBLE',
    etat: 'BON',
    numeroESP32: '',
    dateDerniereMaintenance: '',
    prochaineMaintenance: '',
    actif: true
  });

  useEffect(() => {
    if (camion) {
      setFormData({
        matricule: camion.matricule || '',
        marque: camion.marque || '',
        modele: camion.modele || '',
        annee: camion.annee || '',
        typeCamion: camion.typeCamion || '',
        capacitePoids: camion.capacitePoids || '',
        capaciteReservoir: camion.capaciteReservoir || '',
        kilometrage: camion.kilometrage || '',
        dateMiseEnService: camion.dateMiseEnService || '',
        statut: camion.statut || 'DISPONIBLE',
        etat: camion.etat || 'BON',
        numeroESP32: camion.numeroESP32 || '',
        dateDerniereMaintenance: camion.dateDerniereMaintenance || '',
        prochaineMaintenance: camion.prochaineMaintenance || '',
        actif: camion.actif !== undefined ? camion.actif : true
      });
    } else {
      setFormData({
        matricule: '', marque: '', modele: '', annee: '', typeCamion: '',
        capacitePoids: '', capaciteReservoir: '', kilometrage: '', dateMiseEnService: '',
        statut: 'DISPONIBLE', etat: 'BON', numeroESP32: '', dateDerniereMaintenance: '', prochaineMaintenance: '', actif: true
      });
    }
  }, [camion, isOpen]);

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
    if (!formData.matricule || !formData.marque) {
      toast.error('Le matricule et la marque sont obligatoires');
      return;
    }
    
    // Format numeric values
    const formattedData = {
      ...formData,
      annee: formData.annee ? parseInt(formData.annee) : null,
      capacitePoids: formData.capacitePoids ? parseFloat(formData.capacitePoids) : null,
      capaciteReservoir: formData.capaciteReservoir ? parseFloat(formData.capaciteReservoir) : null,
      kilometrage: formData.kilometrage ? parseFloat(formData.kilometrage) : null,
    };
    
    onSubmit(formattedData);
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
      <div className="modal-content entity-form-modal modal-lg" role="dialog" aria-modal="true" aria-labelledby="camion-form-title" aria-describedby="camion-form-description" onKeyDown={handleDialogKeyDown}>
        <div className="modal-header">
          <div>
            <h2 id="camion-form-title">{camion ? 'Modifier le camion' : 'Ajouter un camion'}</h2>
            <p id="camion-form-description" className="modal-subtitle">Identifiez le véhicule et renseignez sa disponibilité. Les champs marqués * sont obligatoires.</p>
          </div>
          <button type="button" className="close-btn" onClick={onClose} disabled={isLoading} aria-label="Fermer le formulaire"><FiX aria-hidden="true" /></button>
        </div>
        
        <form onSubmit={handleSubmit}>
          <div className="modal-body">
            <section className="form-section" aria-labelledby="camion-identity-title">
              <h3 id="camion-identity-title" className="form-section-title">Identification du véhicule</h3>
              <div className="form-grid">
            <div className="form-group">
              <label htmlFor="camion-matricule">Immatriculation *</label>
              <input ref={firstInputRef} id="camion-matricule" type="text" name="matricule" value={formData.matricule} onChange={handleChange} required placeholder="Ex. 123 TU 4567" />
            </div>
            <div className="form-group">
              <label htmlFor="camion-marque">Marque *</label>
              <input id="camion-marque" type="text" name="marque" value={formData.marque} onChange={handleChange} required placeholder="Ex. Renault" />
            </div>
            <div className="form-group">
              <label htmlFor="camion-modele">Modèle</label>
              <input id="camion-modele" type="text" name="modele" value={formData.modele} onChange={handleChange} />
            </div>
            <div className="form-group">
              <label htmlFor="camion-annee">Année de fabrication</label>
              <input id="camion-annee" type="number" name="annee" value={formData.annee} onChange={handleChange} min="1990" max="2050" />
            </div>
              </div>
            </section>
            <section className="form-section" aria-labelledby="camion-specifications-title">
              <h3 id="camion-specifications-title" className="form-section-title">Caractéristiques techniques</h3>
              <div className="form-grid">
            <div className="form-group">
              <label htmlFor="camion-type">Type de camion</label>
              <select id="camion-type" name="typeCamion" value={formData.typeCamion} onChange={handleChange} className="form-select">
                <option value="">Choisir un type</option>
                <option value="BENNE">Benne à ordures ménagères</option>
                <option value="AMPLIROLL">Ampliroll (Bennes amovibles)</option>
                <option value="PLATEAU">Plateau</option>
              </select>
            </div>
            <div className="form-group">
              <label htmlFor="camion-capacite-poids">Capacité de charge (tonnes)</label>
              <input id="camion-capacite-poids" type="number" step="0.1" name="capacitePoids" value={formData.capacitePoids} onChange={handleChange} />
            </div>
            <div className="form-group">
              <label htmlFor="camion-capacite-reservoir">Réservoir (litres)</label>
              <input id="camion-capacite-reservoir" type="number" step="1" name="capaciteReservoir" value={formData.capaciteReservoir} onChange={handleChange} />
            </div>
            <div className="form-group">
              <label htmlFor="camion-kilometrage">Kilométrage (km)</label>
              <input id="camion-kilometrage" type="number" step="0.1" name="kilometrage" value={formData.kilometrage} onChange={handleChange} />
            </div>
              </div>
            </section>
            <section className="form-section" aria-labelledby="camion-maintenance-title">
              <h3 id="camion-maintenance-title" className="form-section-title">Disponibilité et entretien</h3>
              <div className="form-grid">
            <div className="form-group">
              <label htmlFor="camion-statut">Statut actuel</label>
              <select id="camion-statut" name="statut" value={formData.statut} onChange={handleChange} className="form-select">
                <option value="DISPONIBLE">Disponible</option>
                <option value="EN_TOURNEE">En tournée</option>
                <option value="EN_MAINTENANCE">En maintenance</option>
              </select>
            </div>
            <div className="form-group">
              <label htmlFor="camion-etat">État mécanique</label>
              <select id="camion-etat" name="etat" value={formData.etat} onChange={handleChange} className="form-select">
                <option value="BON">Bon</option>
                <option value="MOYEN">Moyen</option>
                <option value="CRITIQUE">Critique</option>
              </select>
            </div>
            <div className="form-group">
              <label htmlFor="camion-derniere-maintenance">Dernier entretien</label>
              <input id="camion-derniere-maintenance" type="date" name="dateDerniereMaintenance" value={formData.dateDerniereMaintenance} onChange={handleChange} />
            </div>
            <div className="form-group">
              <label htmlFor="camion-prochaine-maintenance">Prochain entretien</label>
              <input id="camion-prochaine-maintenance" type="date" name="prochaineMaintenance" value={formData.prochaineMaintenance} onChange={handleChange} />
            </div>
              </div>
            </section>
            <div className="checkbox-field">
              <input type="checkbox" id="camion-actif" name="actif" checked={formData.actif} onChange={handleChange} aria-describedby="camion-actif-help" />
              <div>
                <label htmlFor="camion-actif">Véhicule actif</label>
                <p id="camion-actif-help" className="field-help">Le véhicule fait partie du parc de collecte.</p>
              </div>
            </div>
          </div>
          
          <div className="modal-footer">
            <button type="button" className="btn-secondary" onClick={onClose} disabled={isLoading}>Annuler</button>
            <button type="submit" className="btn-primary" disabled={isLoading}>
              {isLoading ? 'Enregistrement…' : camion ? 'Enregistrer les modifications' : 'Créer le camion'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default CamionFormModal;
