import { useState, useEffect } from 'react';
import { FiX } from 'react-icons/fi';
import toast from 'react-hot-toast';

const CamionFormModal = ({ isOpen, onClose, onSubmit, camion = null, isLoading = false }) => {
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

  return (
    <div className="modal-overlay">
      <div className="modal-content modal-lg">
        <div className="modal-header">
          <h2>{camion ? 'Modifier le camion' : 'Nouveau camion'}</h2>
          <button className="close-btn" onClick={onClose}><FiX /></button>
        </div>
        
        <form onSubmit={handleSubmit}>
          <div className="modal-body form-grid">
            <h3 className="section-title full-width">Informations générales</h3>
            <div className="form-group">
              <label>Matricule *</label>
              <input type="text" name="matricule" value={formData.matricule} onChange={handleChange} required placeholder="Ex: 12345-A-1" />
            </div>
            <div className="form-group">
              <label>Marque *</label>
              <input type="text" name="marque" value={formData.marque} onChange={handleChange} required placeholder="Ex: Renault" />
            </div>
            <div className="form-group">
              <label>Modèle</label>
              <input type="text" name="modele" value={formData.modele} onChange={handleChange} />
            </div>
            <div className="form-group">
              <label>Année</label>
              <input type="number" name="annee" value={formData.annee} onChange={handleChange} min="1990" max="2050" />
            </div>
            
            <h3 className="section-title full-width mt-3">Caractéristiques techniques</h3>
            <div className="form-group">
              <label>Type de camion</label>
              <select name="typeCamion" value={formData.typeCamion} onChange={handleChange} className="form-select">
                <option value="">Sélectionner</option>
                <option value="BENNE">Benne à ordures ménagères</option>
                <option value="AMPLIROLL">Ampliroll (Bennes amovibles)</option>
                <option value="PLATEAU">Plateau</option>
              </select>
            </div>
            <div className="form-group">
              <label>Capacité Poids (Tonnes)</label>
              <input type="number" step="0.1" name="capacitePoids" value={formData.capacitePoids} onChange={handleChange} />
            </div>
            <div className="form-group">
              <label>Capacité Réservoir (Litres)</label>
              <input type="number" step="1" name="capaciteReservoir" value={formData.capaciteReservoir} onChange={handleChange} />
            </div>
            <div className="form-group">
              <label>Kilométrage</label>
              <input type="number" step="0.1" name="kilometrage" value={formData.kilometrage} onChange={handleChange} />
            </div>
            
            <h3 className="section-title full-width mt-3">État et Maintenance</h3>
            <div className="form-group">
              <label>Statut Actuel</label>
              <select name="statut" value={formData.statut} onChange={handleChange} className="form-select">
                <option value="DISPONIBLE">Disponible</option>
                <option value="EN_TOURNEE">En tournée</option>
                <option value="EN_MAINTENANCE">En maintenance</option>
              </select>
            </div>
            <div className="form-group">
              <label>État Mécanique</label>
              <select name="etat" value={formData.etat} onChange={handleChange} className="form-select">
                <option value="BON">Bon</option>
                <option value="MOYEN">Moyen</option>
                <option value="CRITIQUE">Critique</option>
              </select>
            </div>
            <div className="form-group">
              <label>Dernière Maintenance</label>
              <input type="date" name="dateDerniereMaintenance" value={formData.dateDerniereMaintenance} onChange={handleChange} />
            </div>
            <div className="form-group">
              <label>Prochaine Maintenance</label>
              <input type="date" name="prochaineMaintenance" value={formData.prochaineMaintenance} onChange={handleChange} />
            </div>
            

            <div className="form-group full-width mt-2" style={{display: 'flex', alignItems: 'center', gap: '10px'}}>
              <input type="checkbox" id="actifCamion" name="actif" checked={formData.actif} onChange={handleChange} style={{width: 'auto'}} />
              <label htmlFor="actifCamion" style={{marginBottom: 0}}>Camion actif dans le système</label>
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
        .modal-lg { max-width: 800px; }
        .section-title { font-size: 1.1rem; color: var(--primary); border-bottom: 1px solid var(--border-color); padding-bottom: 0.5rem; margin-bottom: 0.5rem; }
        .mt-3 { margin-top: 1rem; }
        .mt-2 { margin-top: 0.5rem; }
      `}</style>
    </div>
  );
};

export default CamionFormModal;
