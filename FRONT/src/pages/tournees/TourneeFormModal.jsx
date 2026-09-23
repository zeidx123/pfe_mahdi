import { useState, useEffect, useRef } from 'react';
import { FiX, FiMapPin, FiArrowUp, FiArrowDown } from 'react-icons/fi';
import toast from 'react-hot-toast';
import { agentService } from '../../services/agentService';
import { camionService } from '../../services/camionService';
import { pointCollecteService } from '../../services/pointCollecteService';

const TourneeFormModal = ({ isOpen, onClose, onSubmit, tournee = null, isLoading = false }) => {
  const firstInputRef = useRef(null);
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

  useEffect(() => {
    if (!isOpen) return;
    const previousFocus = document.activeElement;
    firstInputRef.current?.focus();
    return () => previousFocus?.focus();
  }, [isOpen]);

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

  const handleDialogKeyDown = (event) => {
    if (event.key === 'Escape' && !isLoading && !localSubmitting) onClose();
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
      <div className="modal-content entity-form-modal modal-xl tournee-form-modal" role="dialog" aria-modal="true" aria-labelledby="tournee-form-title" aria-describedby="tournee-form-description" onKeyDown={handleDialogKeyDown}>
        <div className="modal-header">
          <div>
            <h2 id="tournee-form-title">{tournee ? 'Modifier la tournée' : 'Planifier une tournée'}</h2>
            <p id="tournee-form-description" className="modal-subtitle">Choisissez le chauffeur, le véhicule et les points à collecter. Les champs marqués * sont obligatoires.</p>
          </div>
          <button type="button" className="close-btn" onClick={onClose} disabled={isLoading || localSubmitting} aria-label="Fermer le formulaire"><FiX aria-hidden="true" /></button>
        </div>
        <form onSubmit={handleSubmit}>
          <div className="modal-body">
            <section className="form-section" aria-labelledby="tournee-schedule-title">
              <h3 id="tournee-schedule-title" className="form-section-title">Identification et horaire</h3>
              <div className="form-grid">
              <div className="form-group">
                <label htmlFor="tournee-reference">Référence *</label>
                <input ref={firstInputRef} id="tournee-reference" type="text" name="reference" value={formData.reference} onChange={handleChange} required placeholder={`T-${new Date().getFullYear()}-001`} />
              </div>
              <div className="form-group">
                <label htmlFor="tournee-nom">Nom de la tournée *</label>
                <input id="tournee-nom" type="text" name="nom" value={formData.nom} onChange={handleChange} required placeholder="Ex. Centre-ville · Matin" />
              </div>
              <div className="form-group">
                <label htmlFor="tournee-date">Date prévue *</label>
                <input id="tournee-date" type="date" name="datePrevue" value={formData.datePrevue} onChange={handleChange} required />
              </div>
              <div className="form-group">
                <label htmlFor="tournee-heure-depart">Heure de départ</label>
                <input id="tournee-heure-depart" type="time" name="heureDepartPrevue" value={formData.heureDepartPrevue} onChange={handleChange} />
              </div>
              </div>
            </section>
            <section className="form-section" aria-labelledby="tournee-assignment-title">
              <h3 id="tournee-assignment-title" className="form-section-title">Chauffeur et véhicule</h3>
              <div className="form-grid">
              <div className="form-group">
                <label htmlFor="tournee-camion">Camion disponible *</label>
                <select id="tournee-camion" name="camionId" value={formData.camionId} onChange={handleChange} className="form-select" required aria-describedby={camions.length === 0 ? 'tournee-camion-help' : undefined}>
                  <option value="">Choisir un camion</option>
                  {camions.map(c => (
                    <option key={c.id} value={c.id}>{c.matricule} — {c.marque} {c.modele}</option>
                  ))}
                </select>
                {camions.length === 0 && <p id="tournee-camion-help" className="field-help">Aucun camion disponible. Ajoutez un véhicule actif ou vérifiez son statut.</p>}
              </div>
              <div className="form-group">
                <label htmlFor="tournee-chauffeur">Chauffeur *</label>
                <select id="tournee-chauffeur" name="chauffeurId" value={formData.chauffeurId} onChange={handleChange} className="form-select" required>
                  <option value="">Choisir un chauffeur</option>
                  {agents.map(a => (
                    <option key={a.id} value={a.id}>{a.prenom} {a.nom} ({a.cin})</option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label htmlFor="tournee-statut">Statut initial</label>
                <input id="tournee-statut" value="Planifiée" readOnly aria-describedby="tournee-statut-help" />
                <p id="tournee-statut-help" className="field-help">Le chauffeur démarre et termine la tournée depuis son application.</p>
              </div>
              </div>
            </section>
            <section className="form-section" aria-labelledby="tournee-instructions-title">
              <h3 id="tournee-instructions-title" className="form-section-title">Informations complémentaires</h3>
              <div className="form-grid">
              <div className="form-group full-width">
                <label htmlFor="tournee-description">Description</label>
                <input id="tournee-description" type="text" name="description" value={formData.description} onChange={handleChange} placeholder="Ex. Circuit matinal du centre-ville" />
              </div>
              <div className="form-group full-width">
                <label htmlFor="tournee-notes">Notes</label>
                <input id="tournee-notes" type="text" name="notes" value={formData.notes} onChange={handleChange} placeholder="Consignes ou particularités à connaître" />
              </div>
              </div>
            </section>

            <section className="points-selector form-section" aria-labelledby="tournee-points-title">
              <h3 id="tournee-points-title" className="form-section-title">
                <FiMapPin aria-hidden="true" />{' '}
                Points de collecte ({formData.pointsCollecteIds.length} sélectionnés)
              </h3>
              <div className="points-grid">
                {points.length === 0 ? (
                  <p className="field-help">Aucun point actif disponible. Ajoutez un point de collecte pour composer la tournée.</p>
                ) : points.map(point => (
                  <label key={point.id} htmlFor={`tournee-point-${point.id}`} className={`point-item ${formData.pointsCollecteIds.includes(point.id) ? 'selected' : ''}`}>
                    <input
                      id={`tournee-point-${point.id}`}
                      type="checkbox"
                      checked={formData.pointsCollecteIds.includes(point.id)}
                      onChange={() => togglePoint(point.id)}
                      className="point-choice-input"
                    />
                    <span className="point-check" aria-hidden="true">{formData.pointsCollecteIds.includes(point.id) ? '✓' : ''}</span>
                    <div>
                      <strong>{point.reference}</strong>
                      <p>{point.adresse}</p>
                    </div>
                  </label>
                ))}
              </div>
              {formData.pointsCollecteIds.length > 0 && <div className="manual-route-order">
                <h3 className="form-section-title">Ordre manuel de référence</h3>
                <p className="field-help">Définissez votre circuit habituel. Les algorithmes seront comparés à cet ordre, avec départ et retour au dépôt configuré.</p>
                <ol>{formData.pointsCollecteIds.map((id, index) => {
                  const point = points.find(p => p.id === id) || tournee?.pointsCollecte?.find(p => p.id === id);
                  return <li key={id} style={{ display: 'flex', alignItems: 'center', gap: 8, margin: '8px 0' }}>
                    <span style={{ flex: 1 }}>{index + 1}. {point?.reference || id} — {point?.adresse || 'Point à vérifier'}</span>
                    <button type="button" className="btn-secondary" onClick={() => movePoint(index, -1)} disabled={index === 0} aria-label={`Monter ${point?.reference || id}`}><FiArrowUp /></button>
                    <button type="button" className="btn-secondary" onClick={() => movePoint(index, 1)} disabled={index === formData.pointsCollecteIds.length - 1} aria-label={`Descendre ${point?.reference || id}`}><FiArrowDown /></button>
                  </li>;
                })}</ol>
              </div>}
            </section>
          </div>
          <div className="modal-footer">
            <button type="button" className="btn-secondary" onClick={onClose} disabled={isLoading || localSubmitting}>Annuler</button>
            <button type="submit" className="btn-primary" disabled={isLoading || localSubmitting}>
              {(isLoading || localSubmitting) ? 'Enregistrement…' : tournee ? 'Enregistrer les modifications' : 'Créer la tournée'}
            </button>
          </div>
        </form>
      </div>
      <style>{`
        .modal-xl { max-width: 860px; }
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
