import { useState, useEffect } from 'react';
import { FiX, FiMapPin, FiArrowUp, FiArrowDown } from 'react-icons/fi';
import toast from 'react-hot-toast';
import { agentService } from '../../services/agentService';
import { camionService } from '../../services/camionService';
import { pointCollecteService } from '../../services/pointCollecteService';

const TourneeFormModal = ({ isOpen, onClose, onSubmit, tournee = null, isLoading = false }) => {
  const [formData, setFormData] = useState({
    reference: '', nom: '', description: '', datePrevue: '',
    heureDepartPrevue: '', camionId: '', chauffeurId: '',
    pointsCollecteIds: [], statut: 'PLANIFIEE', notes: ''
  });
  const [agents, setAgents] = useState([]);
  const [camions, setCamions] = useState([]);
  const [points, setPoints] = useState([]);
  const [localSubmitting, setLocalSubmitting] = useState(false);

  // Proposition de référence modifiable ; le serveur contrôle les doublons.
  const generateReference = () => {
    const year = new Date().getFullYear();
    const rand = Math.floor(100 + Math.random() * 900); // 3 chiffres
    return `T-${year}-${rand}`;
  };

  useEffect(() => {
    if (isOpen) {
      loadResources();
    }
  }, [isOpen]);

  useEffect(() => {
    if (tournee) {
      setFormData({
        reference: tournee.reference || '',
        nom: tournee.nom || '',
        description: tournee.description || '',
        datePrevue: tournee.datePrevue || '',
        heureDepartPrevue: tournee.heureDepartPrevue || '',
        camionId: tournee.camionId || '',
        chauffeurId: tournee.chauffeurId || '',
        pointsCollecteIds: tournee.pointsCollecteIds || [],
        statut: 'PLANIFIEE',
        notes: tournee.notes || ''
      });
    } else {
      setFormData({
        reference: generateReference(),
        nom: '', description: '', datePrevue: '',
        heureDepartPrevue: '', camionId: '', chauffeurId: '',
        pointsCollecteIds: [], statut: 'PLANIFIEE', notes: ''
      });
    }
    setLocalSubmitting(false);
  }, [tournee, isOpen]);

  const loadResources = async () => {
    try {
      const [agentsData, camionsData, pointsData] = await Promise.all([
        agentService.getAll(),
        camionService.getAll(),
        pointCollecteService.getAll()
      ]);
      setAgents(agentsData.filter(a => a.actif));
      setCamions(camionsData.filter(c => c.actif && c.statut === 'DISPONIBLE'));
      setPoints(pointsData.filter(p => p.actif));
    } catch {
      toast.error('Erreur lors du chargement des ressources');
    }
  };

  if (!isOpen) return null;

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(previous => ({ ...previous, [name]: value }));
  };

  const togglePoint = (pointId) => {
    setFormData(previous => ({ ...previous, pointsCollecteIds: previous.pointsCollecteIds.includes(pointId)
      ? previous.pointsCollecteIds.filter(id => id !== pointId) : [...previous.pointsCollecteIds, pointId] }));
  };

  const movePoint = (index, direction) => {
    setFormData(previous => {
      const ids = [...previous.pointsCollecteIds];
      const target = index + direction;
      if (target < 0 || target >= ids.length) return previous;
      [ids[index], ids[target]] = [ids[target], ids[index]];
      return { ...previous, pointsCollecteIds: ids };
    });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (localSubmitting || isLoading) return; // Bloquer les doubles soumissions
    if (!formData.reference || !formData.nom || !formData.datePrevue || !formData.camionId || !formData.chauffeurId) {
      toast.error('Veuillez remplir tous les champs obligatoires');
      return;
    }
    if (formData.pointsCollecteIds.length === 0) {
      toast.error('Sélectionnez au moins un point de collecte');
      return;
    }
    setLocalSubmitting(true);
    try { await onSubmit({ ...formData, statut: 'PLANIFIEE' }); }
    finally { setLocalSubmitting(false); }
  };

  return (
    <div className="modal-overlay">
      <div className="modal-content modal-xl">
        <div className="modal-header">
          <h2>{tournee ? 'Modifier la tournée' : 'Nouvelle tournée'}</h2>
          <button className="close-btn" onClick={onClose}><FiX /></button>
        </div>
        <form onSubmit={handleSubmit}>
          <div className="modal-body">
            <div className="form-grid">
              <div className="form-group">
                <label>Référence * <small>(ex: T-2024-001)</small></label>
                <input type="text" name="reference" value={formData.reference} onChange={handleChange} required placeholder="T-2024-001" />
              </div>
              <div className="form-group">
                <label>Nom de la tournée *</label>
                <input type="text" name="nom" value={formData.nom} onChange={handleChange} required placeholder="Tournée Centre-Ville Matin" />
              </div>
              <div className="form-group">
                <label>Date prévue *</label>
                <input type="date" name="datePrevue" value={formData.datePrevue} onChange={handleChange} required />
              </div>
              <div className="form-group">
                <label>Heure de départ</label>
                <input type="time" name="heureDepartPrevue" value={formData.heureDepartPrevue} onChange={handleChange} />
              </div>
              <div className="form-group">
                <label>Camion (disponibles) *</label>
                <select name="camionId" value={formData.camionId} onChange={handleChange} className="form-select" required>
                  <option value="">-- Sélectionner un camion --</option>
                  {camions.map(c => (
                    <option key={c.id} value={c.id}>🚛 {c.matricule} — {c.marque} {c.modele}</option>
                  ))}
                </select>
                {camions.length === 0 && <small style={{color:'#f59e0b'}}>Aucun camion disponible</small>}
              </div>
              <div className="form-group">
                <label>Chauffeur *</label>
                <select name="chauffeurId" value={formData.chauffeurId} onChange={handleChange} className="form-select" required>
                  <option value="">-- Sélectionner un chauffeur --</option>
                  {agents.map(a => (
                    <option key={a.id} value={a.id}>👤 {a.prenom} {a.nom} ({a.cin})</option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label>Statut</label>
                <input value="Planifiée" readOnly />
                <small>Le chauffeur démarre et termine la tournée depuis son application.</small>
              </div>
              <div className="form-group full-width">
                <label>Description</label>
                <input type="text" name="description" value={formData.description} onChange={handleChange} placeholder="Circuit matinal du centre-ville..." />
              </div>
              <div className="form-group full-width">
                <label>Notes</label>
                <input type="text" name="notes" value={formData.notes} onChange={handleChange} placeholder="Remarques particulières..." />
              </div>
            </div>

            <div className="points-selector">
              <h3 className="section-title mt-3">
                <FiMapPin style={{marginRight:6}} />
                Points de collecte ({formData.pointsCollecteIds.length} sélectionnés)
              </h3>
              <div className="points-grid">
                {points.length === 0 ? (
                  <p style={{color:'var(--text-muted)',fontSize:13}}>Aucun point actif disponible.</p>
                ) : points.map(point => (
                  <label key={point.id} className={`point-item ${formData.pointsCollecteIds.includes(point.id) ? 'selected' : ''}`}>
                    <input
                      type="checkbox"
                      checked={formData.pointsCollecteIds.includes(point.id)}
                      onChange={() => togglePoint(point.id)}
                      className="point-choice-input"
                    />
                    <span className="point-check">{formData.pointsCollecteIds.includes(point.id) ? '✓' : ''}</span>
                    <div>
                      <strong>{point.reference}</strong>
                      <p>{point.adresse}</p>
                    </div>
                  </label>
                ))}
              </div>
              {formData.pointsCollecteIds.length > 0 && <div className="manual-route-order">
                <h3 className="section-title mt-3">Ordre manuel de référence</h3>
                <p style={{ fontSize: 12, color: 'var(--text-muted)' }}>Définissez votre circuit habituel. Les algorithmes seront comparés à cet ordre, avec départ et retour au dépôt configuré.</p>
                <ol>{formData.pointsCollecteIds.map((id, index) => {
                  const point = points.find(p => p.id === id) || tournee?.pointsCollecte?.find(p => p.id === id);
                  return <li key={id} style={{ display: 'flex', alignItems: 'center', gap: 8, margin: '8px 0' }}>
                    <span style={{ flex: 1 }}>{index + 1}. {point?.reference || id} — {point?.adresse || 'Point à vérifier'}</span>
                    <button type="button" className="btn-secondary" onClick={() => movePoint(index, -1)} disabled={index === 0} aria-label={`Monter ${point?.reference || id}`}><FiArrowUp /></button>
                    <button type="button" className="btn-secondary" onClick={() => movePoint(index, 1)} disabled={index === formData.pointsCollecteIds.length - 1} aria-label={`Descendre ${point?.reference || id}`}><FiArrowDown /></button>
                  </li>;
                })}</ol>
              </div>}
            </div>
          </div>
          <div className="modal-footer">
            <button type="button" className="btn-secondary" onClick={onClose} disabled={isLoading || localSubmitting}>Annuler</button>
            <button type="submit" className="btn-primary" disabled={isLoading || localSubmitting}>
              {(isLoading || localSubmitting) ? 'Enregistrement...' : 'Enregistrer'}
            </button>
          </div>
        </form>
      </div>
      <style>{`
        .modal-xl { max-width: 860px; }
        .section-title { font-size: 1rem; color: var(--primary); border-bottom: 1px solid var(--border-color); padding-bottom: 0.5rem; margin-bottom: 0.75rem; display: flex; align-items: center; }
        .mt-3 { margin-top: 1.25rem; }
        .points-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 8px; max-height: 220px; overflow-y: auto; padding: 4px; }
        .point-item { display: flex; align-items: flex-start; gap: 10px; padding: 10px; border: 2px solid var(--border-color); border-radius: 8px; cursor: pointer; transition: all 0.2s; }
        .point-choice-input { position: absolute; width: 1px; height: 1px; padding: 0; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; border: 0; }
        .point-item:focus-within { outline: 2px solid var(--primary); outline-offset: 2px; }
        .point-item:hover { border-color: var(--primary); background: #f0fdf4; }
        .point-item.selected { border-color: var(--primary); background: #f0fdf4; }
        .point-check { width: 20px; height: 20px; min-width: 20px; border: 2px solid var(--primary); border-radius: 4px; display: flex; align-items: center; justify-content: center; font-size: 12px; color: var(--primary); font-weight: 700; }
        .point-item strong { font-size: 13px; }
        .point-item p { font-size: 11px; color: var(--text-muted); margin: 2px 0 0; }
      `}</style>
    </div>
  );
};

export default TourneeFormModal;
