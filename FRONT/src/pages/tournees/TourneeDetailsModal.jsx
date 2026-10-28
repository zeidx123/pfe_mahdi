import { useEffect, useRef, useState } from 'react';
import { MapContainer, TileLayer, Polyline, CircleMarker, Popup, useMap } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import { FiX, FiMapPin, FiCalendar, FiTruck, FiUser, FiCamera, FiCheck, FiAlertTriangle, FiRefreshCw, FiFileText } from 'react-icons/fi';
import { tourneeService } from '../../services/tourneeService';
import { rapportService } from '../../services/rapportService';
import { aiService } from '../../services/aiService';
import ProtectedImage from '../../components/ProtectedImage';
import toast from 'react-hot-toast';

const assessmentLabels = {
  CLEANED: 'Le point apparaît nettoyé', NOT_CLEANED: 'Le point ne paraît pas nettoyé',
  OBSTRUCTED: 'Point obstrué', CANNOT_DETERMINE: 'Impossible à déterminer', ANALYSIS_UNAVAILABLE: 'Analyse indisponible',
};
// Moteur de vision local uniquement : ces champs sont absents des analyses LLM.
const qualityLabels = { TROP_SOMBRE: 'trop sombre', SUREXPOSEE: 'surexposée', FLOUE: 'floue' };
const percent = value => `${Math.round(value * 100)} %`;
const probabilitiesText = p => p && ['PROPRE', 'DEBORDEMENT', 'CANNOT_DETERMINE'].every(k => Number.isFinite(p[k]))
  ? `propre ${percent(p.PROPRE)} · débordement ${percent(p.DEBORDEMENT)} · indéterminé ${percent(p.CANNOT_DETERMINE)}` : null;
const narrativeNotices = {
  LLM: 'Texte rédigé par un modèle de langage à partir des seules probabilités ; il n’a pas vu la photo. À confirmer par un contrôle humain.',
  TEMPLATE: 'Texte standard (modèle de langage désactivé ou indisponible).',
};
const analysisDetails = result => ({ description: result?.description || null, provider: result?.provider || null,
  narrative: result?.narrative || null, narrativeSource: result?.narrativeSource || null });
const visitLabels = { PENDING: 'À visiter', ARRIVED: 'Arrivé', COMPLETED_REPORTED: 'Collecte déclarée', ISSUE_REPORTED: 'Problème signalé' };
const tourStatusLabels = { PLANIFIEE: 'Planifiée', EN_COURS: 'En cours', TERMINEE: 'Terminée', ANNULEE: 'Annulée' };
const algorithmLabels = { MANUAL: 'Ordre manuel', NN: 'Plus proche voisin', NN_2OPT: 'Plus proche voisin + 2-opt', EXACT: 'Exact (Held–Karp)' };
const seconds = value => Number.isFinite(value) ? `${(value / 60).toFixed(1)} min` : 'Non mesuré';
const kilometers = value => Number.isFinite(value) ? `${(value / 1000).toFixed(2)} km` : 'Non disponible';
const when = value => value && !Number.isNaN(new Date(value).getTime()) ? new Date(value).toLocaleString('fr-FR', { dateStyle: 'medium', timeStyle: 'short' }) : 'Non enregistré';
const plannedWhen = (date, time) => {
  if (!date) return 'Date non renseignée';
  const planned = new Date(`${date}T${time || '00:00:00'}`);
  if (Number.isNaN(planned.getTime())) return 'Date non renseignée';
  return `${planned.toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' })}${time ? ` · ${planned.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}` : ''}`;
};
const coordinate = point => Number.isFinite(point?.latitude) && Number.isFinite(point?.longitude) && Math.abs(point.latitude) <= 90 && Math.abs(point.longitude) <= 180 ? [point.latitude, point.longitude] : null;

function FitRoute({ coordinates }) {
  const map = useMap();
  useEffect(() => {
    if (coordinates.length > 1) map.fitBounds(coordinates, { padding: [24, 24], maxZoom: 16 });
    else if (coordinates.length === 1) map.setView(coordinates[0], 14);
  }, [coordinates, map]);
  return null;
}

function RouteMap({ route, points }) {
  const geometry = (route.geometry || []).filter(p => Array.isArray(p) && p.length >= 2 && Number.isFinite(p[0]) && Number.isFinite(p[1])).map(([lon, lat]) => [lat, lon]);
  const depot = coordinate(route.depot);
  const visible = geometry.length ? geometry : [depot, ...points.map(coordinate)].filter(Boolean);
  if (!visible.length) return <p>Géométrie routière indisponible.</p>;
  return <MapContainer center={visible[0]} zoom={13} style={{ height: 290, width: '100%', borderRadius: 10 }} scrollWheelZoom={false}>
    <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>' />
    <FitRoute coordinates={visible} />
    {geometry.length > 0 && <Polyline positions={geometry} pathOptions={{ color: '#209b75', weight: 4 }} />}
    {depot && <CircleMarker center={depot} radius={9} pathOptions={{ color: '#22313e', fillOpacity: 1 }}><Popup>{route.depot?.label || 'Dépôt'} · départ et retour</Popup></CircleMarker>}
    {points.map((point, i) => coordinate(point) && <CircleMarker key={point.id} center={coordinate(point)} radius={7} pathOptions={{ color: '#187d5e', fillOpacity: 1 }}><Popup>{i + 1}. {point.reference}<br />{point.adresse}</Popup></CircleMarker>)}
  </MapContainer>;
}

function VisitEvidence({ tourId, point, visit, onRefresh, onZoom }) {
  const [label, setLabel] = useState(visit.reviewLabel || '');
  const [notes, setNotes] = useState(visit.reviewNotes || '');
  const [saving, setSaving] = useState(false);
  const [analysis, setAnalysis] = useState({ id: null, description: null });
  const [narrating, setNarrating] = useState(false);
  const historyMissing = Boolean(visit.legacyProgressMissing);
  const probabilities = probabilitiesText(visit.analysisProbabilities);
  const current = analysis.id === visit.analysisId ? analysis : {};
  const description = current.description || null;
  useEffect(() => {
    if (!visit.analysisId) return undefined;
    let active = true;
    // The justification is stored with the analysis, not with the visit.
    aiService.getPhotoAnalysis(visit.analysisId)
      .then(result => { if (active) setAnalysis({ id: visit.analysisId, ...analysisDetails(result) }); })
      .catch(() => { if (active) setAnalysis({ id: visit.analysisId, description: null }); });
    return () => { active = false; };
  }, [visit.analysisId]);
  const handled = !historyMissing && ['COMPLETED_REPORTED', 'ISSUE_REPORTED'].includes(visit.status);
  // Optional wording of a LOCAL verdict; the status, decision and standard description never change.
  const narrate = async () => {
    if (!visit.analysisId || narrating) return;
    setNarrating(true);
    try {
      const result = await aiService.generatePhotoNarrative(visit.analysisId);
      setAnalysis({ id: visit.analysisId, ...analysisDetails(result) });
    } catch (error) {
      toast.error(error.response?.data?.message || 'Résumé non généré. Réessayez.');
    } finally { setNarrating(false); }
  };
  const review = async event => {
    event.preventDefault();
    if (!label || saving) return;
    setSaving(true);
    try {
      const updated = await tourneeService.review(tourId, point.id, { label, notes: notes.trim() });
      await onRefresh?.(updated);
      toast.success('Avis du superviseur enregistré');
    } catch (error) {
      toast.error(error.response?.data?.message || 'Avis non enregistré. Réessayez.');
    } finally { setSaving(false); }
  };
  return <article className={`visit-evidence ${handled ? 'handled' : ''}`}>
    <div className="visit-heading">
      <span className="point-number">{(visit.order ?? 0) + 1}</span>
      <div className="point-info"><strong>{point.reference || point.id}</strong><span>{point.adresse}</span></div>
      <span className={visit.status === 'ISSUE_REPORTED' ? 'badge-issue' : handled ? 'badge-valid' : 'badge-pending'}>
        {handled && visit.status !== 'ISSUE_REPORTED' ? <FiCheck /> : <FiAlertTriangle />} {historyMissing ? 'Suivi historique non renseigné' : visitLabels[visit.status] || 'Historique sans suivi de visite'}
      </span>
    </div>
    <p className="visit-meta">Arrivée : {when(visit.arrivedAt)} · Fin : {when(visit.completedAt)} · Service prévu : {seconds(visit.plannedServiceSeconds)}</p>
    <div className="evidence-grid">
      {visit.beforePhoto && <figure><figcaption>AVANT (facultative)</figcaption><ProtectedImage path={visit.beforePhoto} alt={`Avant collecte — ${point.reference || point.id}`} className="evidence-image" onZoom={onZoom} /></figure>}
      {visit.afterPhoto ? <figure><figcaption>APRÈS</figcaption><ProtectedImage path={visit.afterPhoto} alt={`Après collecte — ${point.reference || point.id}`} className="evidence-image" onZoom={onZoom} /></figure> : <p className="visit-meta">Aucune photo identifiée comme APRÈS.</p>}
      {visit.legacyPhoto && <figure><figcaption>Photo historique — étape inconnue (AVANT/APRÈS)</figcaption><ProtectedImage path={visit.legacyPhoto} alt={`Photo historique, étape inconnue — ${point.reference || point.id}`} className="evidence-image" onZoom={onZoom} /></figure>}
    </div>
    <div className="visit-assessments">
      <p><strong>Observation IA :</strong> {assessmentLabels[visit.analysisStatus] || 'Aucune analyse disponible'}</p>
      {probabilities && <p><strong>Probabilités (modèle local) :</strong> {probabilities}</p>}
      {visit.analysisDecision === 'A_VERIFIER' && <p><span className="badge-pending"><FiAlertTriangle /> À vérifier par le superviseur</span></p>}
      {qualityLabels[visit.analysisQualityIssue] && <p><strong>Photo à refaire :</strong> {qualityLabels[visit.analysisQualityIssue]}</p>}
      {description && <p><strong>Justification de l’analyse :</strong> {description}</p>}
      {current.narrative && <div className="visit-narrative">
        <p><strong>Résumé rédigé :</strong> {current.narrative}</p>
        <p className="visit-meta">{narrativeNotices[current.narrativeSource] || narrativeNotices.TEMPLATE}</p>
      </div>}
      {current.provider === 'LOCAL' && current.narrativeSource !== 'LLM' && <p>
        <button type="button" className="btn-secondary" onClick={narrate} disabled={narrating}>
          {narrating ? 'Rédaction…' : 'Générer un résumé rédigé'}
        </button>
      </p>}
      {visit.driverAssessment && <p><strong>Observation du chauffeur :</strong> {assessmentLabels[visit.driverAssessment] || visit.driverAssessment}</p>}
      {visit.notes && <p><strong>Note du chauffeur :</strong> {visit.notes}</p>}
      {visit.reviewLabel && <p><strong>Avis du superviseur :</strong> {assessmentLabels[visit.reviewLabel]}{visit.reviewNotes ? ` — ${visit.reviewNotes}` : ''}</p>}
    </div>
    {(handled || visit.afterPhoto) && <form onSubmit={review} className="visit-review">
      <label>Avis humain
        <select className="form-select" value={label} onChange={e => setLabel(e.target.value)} required disabled={saving}>
          <option value="">Sélectionner une observation</option>
          {Object.entries(assessmentLabels).filter(([key]) => key !== 'ANALYSIS_UNAVAILABLE').map(([key, text]) => <option key={key} value={key}>{text}</option>)}
        </select>
      </label>
      <label>Commentaire de revue
        <textarea value={notes} onChange={e => setNotes(e.target.value)} rows={2} maxLength={2000} disabled={saving} />
      </label>
      <button className="btn-primary" disabled={!label || saving}>{saving ? 'Enregistrement…' : 'Enregistrer l’avis'}</button>
    </form>}
  </article>;
}

export default function TourneeDetailsModal({ isOpen, onClose, tournee, onRefresh }) {
  const [zoomedImage, setZoomedImage] = useState(null);
  const [reoptimising, setReoptimising] = useState(false);
  const closeButtonRef = useRef(null);
  const zoomCloseButtonRef = useRef(null);
  const tourId = tournee?.id;

  useEffect(() => {
    if (!isOpen || !tourId) return;
    const previousFocus = document.activeElement;
    closeButtonRef.current?.focus();
    return () => { if (previousFocus?.isConnected) previousFocus.focus(); };
  }, [isOpen, tourId]);

  useEffect(() => {
    if (!zoomedImage) return;
    const previousFocus = document.activeElement;
    zoomCloseButtonRef.current?.focus();
    return () => { if (previousFocus?.isConnected) previousFocus.focus(); };
  }, [zoomedImage]);

  if (!isOpen || !tournee) return null;
  const route = tournee.route;
  const points = tournee.pointsCollecte || [];
  const pointById = new Map(points.map(p => [p.id, p]));
  const visits = tournee.visits?.length ? [...tournee.visits].sort((a, b) => a.order - b.order)
    : points.map((p, order) => ({ pointId: p.id, order, status: 'PENDING', legacyProgressMissing: ['EN_COURS', 'TERMINEE', 'ANNULEE'].includes(tournee.statut), legacyPhoto: tournee.photosPoints?.[p.id] }));
  const orderedPoints = visits.map(v => pointById.get(v.pointId) || { id: v.pointId });
  const historyMissing = visits.some(v => v.legacyProgressMissing);
  const completed = visits.filter(v => !v.legacyProgressMissing && ['COMPLETED_REPORTED', 'ISSUE_REPORTED'].includes(v.status)).length;
  const baseline = route?.comparisons?.find(c => c.algorithm === 'MANUAL');
  const exact = route?.comparisons?.find(c => c.algorithm === 'EXACT');
  const estimate = tournee.estimatedAtStartSeconds ?? route?.totalSeconds;
  const actual = tournee.actualDurationSeconds;
  const difference = Number.isFinite(actual) && Number.isFinite(estimate) ? actual - estimate : null;

  const handleDialogKeyDown = event => {
    if (event.key === 'Escape') {
      event.stopPropagation();
      if (zoomedImage) setZoomedImage(null);
      else onClose();
    }
    if (event.key !== 'Tab') return;
    const container = zoomedImage ? event.currentTarget.querySelector('.image-zoom-overlay') : event.currentTarget;
    const controls = [...container.querySelectorAll('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), a[href], [tabindex]:not([tabindex="-1"])')];
    const first = controls[0];
    const last = controls[controls.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last?.focus();
    } else if (!controls.includes(document.activeElement) || (!event.shiftKey && document.activeElement === last)) {
      event.preventDefault();
      first?.focus();
    }
  };

  const handleDownloadPdf = async () => {
    try { await rapportService.downloadTourneePdf(tournee.id, tournee.reference); toast.success('Rapport PDF téléchargé'); }
    catch { toast.error('Erreur lors du téléchargement du PDF'); }
  };
  const handleReoptimiser = async () => {
    if (reoptimising) return;
    setReoptimising(true);
    try {
      const updated = await tourneeService.reoptimiser(tournee.id);
      await onRefresh?.(updated);
      toast.success('Parcours routier recalculé');
    } catch (error) {
      try { await onRefresh?.(await tourneeService.getById(tournee.id)); } catch { /* Keep the visible error if refresh also fails. */ }
      toast.error(error.response?.data?.message || 'Parcours routier indisponible. Vérifiez le dépôt, les points et le service de cartes.');
    }
    finally { setReoptimising(false); }
  };

  return <div className="modal-overlay">
    <div className="modal-content modal-lg tour-execution-detail" role="dialog" aria-modal="true" aria-labelledby="tour-detail-title" aria-describedby="tour-detail-description" onKeyDown={handleDialogKeyDown}>
      <div className="modal-header">
        <div className="tour-detail-heading">
          <h2 id="tour-detail-title"><span className="tour-detail-reference">{tournee.reference || 'Tournée de collecte'}</span>{tournee.nom || 'Détails de la tournée'}</h2>
          <p id="tour-detail-description" className="modal-subtitle">Parcours, avancement de la collecte et revue des observations.</p>
        </div>
        <div className="tour-detail-header-actions">
          <button type="button" className="btn-secondary" onClick={handleDownloadPdf}><FiFileText aria-hidden="true" /> Rapport PDF</button>
          <button ref={closeButtonRef} type="button" className="close-btn" onClick={onClose} aria-label="Fermer les détails"><FiX aria-hidden="true" /></button>
        </div>
      </div>
      <div className="modal-body" style={{ maxHeight: 'calc(100vh - 160px)', overflowY: 'auto' }}>
        <div className="info-grid-details">
          <div className="info-card"><FiCalendar /><div><span>Date prévue</span><strong>{plannedWhen(tournee.datePrevue, tournee.heureDepartPrevue)}</strong></div></div>
          <div className="info-card"><FiTruck /><div><span>Camion</span><strong>{tournee.camionMatricule || 'Non affecté'}</strong></div></div>
          <div className="info-card"><FiUser /><div><span>Chauffeur</span><strong>{tournee.chauffeurNomComplet || 'Non affecté'}</strong></div></div>
          <div className="info-card"><FiMapPin /><div><span>{tourStatusLabels[tournee.statut] || 'Statut non renseigné'}</span><strong>{historyMissing ? `${completed} visite(s) confirmée(s) · historique incomplet` : `${completed} / ${visits.length} points traités`}</strong></div></div>
        </div>
        {historyMissing && <p className="notes-box">Le suivi de certains points n’a pas été enregistré dans cette ancienne tournée. Leur avancement historique reste inconnu ; les photos historiques sans étape précisée ne sont pas considérées comme des photos APRÈS.</p>}
        {tournee.description && <p className="notes-box">{tournee.description}</p>}
        <section className="optim-card">
          <div className="optim-header"><div><h3>Parcours routier</h3><p className="field-help">Départ et retour au dépôt configuré</p></div>
            <button className="btn-primary" onClick={handleReoptimiser} disabled={reoptimising || tournee.statut !== 'PLANIFIEE'}><FiRefreshCw /> {reoptimising ? 'Calcul en cours…' : 'Calculer et comparer'}</button>
          </div>
          {tournee.routeError && <p role="alert" className="notes-box">{tournee.routeError}</p>}
          {route ? <>
            <p><strong>{route.depot?.label || 'Dépôt configuré'}</strong> → {orderedPoints.map(p => p.reference || p.id).join(' → ')} → <strong>{route.depot?.label || 'Dépôt'}</strong></p>
            <div className="optim-metrics">
              <div><span>Distance routière</span><strong>{kilometers(route.distanceMeters)}</strong></div>
              <div><span>Trajet estimé</span><strong>{seconds(route.travelSeconds)}</strong></div>
              <div><span>Service prévu</span><strong>{seconds(route.serviceSeconds)}</strong></div>
              <div><span>Total prévu</span><strong>{seconds(route.totalSeconds)}</strong></div>
            </div>
            <RouteMap route={route} points={orderedPoints} />
            <p className="visit-meta">Méthode retenue : {algorithmLabels[route.algorithm] || route.algorithm}. Profil routier : {route.profile}. Calcul : {when(route.calculatedAt)}. Sans trafic en temps réel ni contraintes de gabarit camion.</p>
            {route.comparisons?.length > 0 && <div className="table-container"><table className="data-table"><thead><tr><th>Méthode</th><th>Distance</th><th>Trajet</th><th>Gain / manuel</th><th>Écart / exact</th><th>Calcul</th></tr></thead>
              <tbody>{route.comparisons.map(c => <tr key={c.algorithm} className={c.algorithm === route.algorithm ? 'selected-route' : ''}>
                <td>{algorithmLabels[c.algorithm] || c.algorithm}{c.algorithm === route.algorithm ? ' — retenue' : ''}</td><td>{kilometers(c.distanceMeters)}</td><td>{seconds(c.travelSeconds)}</td>
                <td>{baseline?.travelSeconds > 0 ? `${((baseline.travelSeconds - c.travelSeconds) / baseline.travelSeconds * 100).toFixed(1)} %` : '—'}</td>
                <td>{exact?.travelSeconds > 0 ? `${((c.travelSeconds - exact.travelSeconds) / exact.travelSeconds * 100).toFixed(1)} %` : '—'}</td>
                <td>{Number.isFinite(c.runtimeMillis) ? `${c.runtimeMillis.toFixed(2)} ms` : '—'}</td>
              </tr>)}</tbody></table></div>}
            <p className="visit-meta">Comparaison sur la même matrice routière dirigée. La meilleure heuristique testée est retenue ; le benchmark exact s’applique uniquement aux petites instances. Le service prévu est identique pour chaque ordre.</p>
          </> : <p className="notes-box">Parcours routier non calculé. Vérifiez les coordonnées des points, le dépôt et la configuration du fournisseur, puis lancez le calcul avant le départ.</p>}
        </section>
        <section className="execution-timing"><h3>Durées prévues et observées</h3>
          <div className="optim-metrics">
            <div><span>Estimation au départ</span><strong>{seconds(tournee.estimatedAtStartSeconds)}</strong></div>
            <div><span>Durée réelle totale</span><strong>{seconds(actual)}</strong></div>
            <div><span>Écart réel − prévu</span><strong>{difference === null ? 'Non mesuré' : `${difference >= 0 ? '+' : ''}${seconds(difference)}`}</strong></div>
            <div><span>Service observé</span><strong>{seconds(tournee.observedServiceSeconds)}</strong><small>{tournee.measuredServiceStops ?? 0} point(s) mesuré(s)</small></div>
          </div>
          <p className="visit-meta">Départ : {when(tournee.heureDebutReelle)} · Fin : {when(tournee.heureFinReelle)} · Retour au dépôt : {tournee.returnedToDepot ? 'confirmé' : 'non confirmé'}. La durée écoulée peut inclure pauses et attentes ; les données manquantes ne sont pas remplacées par des estimations.</p>
        </section>
        <section><h3><FiCamera /> Collecte, preuves et revue humaine</h3><p className="visit-meta">Une photo APRÈS est requise pour une collecte normale ; la photo AVANT est facultative. L’avancement déclaré et l’évaluation de propreté sont distincts.</p>
          {visits.map(visit => <VisitEvidence key={`${tournee.id}:${visit.pointId}:${visit.reviewedAt || ''}`} tourId={tournee.id} point={pointById.get(visit.pointId) || { id: visit.pointId }} visit={visit} onRefresh={onRefresh} onZoom={setZoomedImage} />)}
        </section>
        {tournee.photoFin && <section><h3>Photo de fin de service (facultative)</h3><ProtectedImage path={tournee.photoFin} alt="Fin de service" className="evidence-image" onZoom={setZoomedImage} /></section>}
      </div>
      {zoomedImage && <div className="image-zoom-overlay" role="dialog" aria-modal="true" aria-label="Photo de collecte agrandie" onClick={() => setZoomedImage(null)}>
        <button ref={zoomCloseButtonRef} type="button" className="close-zoom-btn" onClick={() => setZoomedImage(null)} aria-label="Fermer la photo"><FiX /></button>
        <ProtectedImage path={zoomedImage} alt="Preuve de collecte agrandie" className="zoomed-img" />
      </div>}
    </div>
    <style>{`
      .modal-content.tour-execution-detail { max-width: 1050px; }
      .tour-execution-detail .tour-detail-heading { flex: 1; min-width: 0; }
      .tour-execution-detail .tour-detail-reference { display: block; color: var(--primary-hover); font-size: 11px; font-weight: 650; letter-spacing: .5px; margin-bottom: 5px; }
      .tour-execution-detail .tour-detail-heading h2 { overflow-wrap: anywhere; }
      .tour-execution-detail .tour-detail-header-actions { display: flex; align-items: flex-start; gap: 10px; flex-shrink: 0; }
      .tour-execution-detail h3 { font-size: 16px; color: var(--text-main); margin: 0 0 12px; }
      .tour-execution-detail section { margin: 24px 0; }
      .tour-execution-detail .info-grid-details { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
      .tour-execution-detail .info-card { display: flex; align-items: flex-start; gap: 10px; padding: 15px 13px; background: #f7f9f8; border: 1px solid var(--border-color); border-radius: 10px; min-width: 0; }
      .tour-execution-detail .info-card > div { min-width: 0; }
      .tour-execution-detail .info-card svg { color: var(--primary-hover); flex-shrink: 0; margin-top: 3px; }
      .tour-execution-detail .info-card span { display: block; font-size: 11px; color: var(--text-muted); margin-bottom: 4px; }
      .tour-execution-detail .info-card strong { display: block; color: var(--text-main); font-size: 13px; font-weight: 600; line-height: 1.5; overflow-wrap: anywhere; }
      .tour-execution-detail .notes-box { background: #f5f8f6; border: 1px solid var(--border-color); border-left: 3px solid #b2c9bd; color: #516259; border-radius: 8px; padding: 13px 15px; margin: 14px 0; font-size: 13px; line-height: 1.6; }
      .tour-execution-detail .notes-box[role="alert"] { background: #fbf5f2; border-color: #eadbd4; border-left-color: #bc7862; color: #874d3b; }
      .tour-execution-detail .optim-card { background: #f8fbf9; border: 1px solid #d8e8df; padding: 20px; border-radius: 12px; }
      .tour-execution-detail .optim-header { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 16px; }
      .tour-execution-detail .optim-header h3 { margin-bottom: 3px; }
      .tour-execution-detail .optim-header button { display: flex; align-items: center; gap: 6px; }
      .tour-execution-detail .optim-card > p { font-size: 13px; line-height: 1.65; }
      .tour-execution-detail .optim-metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin: 16px 0; }
      .tour-execution-detail .optim-metrics > div { background: var(--bg-card); padding: 15px 13px; border: 1px solid var(--border-color); border-radius: 9px; min-width: 0; }
      .tour-execution-detail .optim-metrics span { display: block; color: var(--text-muted); font-size: 11px; }
      .tour-execution-detail .optim-metrics strong { display: block; color: var(--text-main); font-size: 19px; font-weight: 650; font-variant-numeric: tabular-nums; margin-top: 5px; }
      .tour-execution-detail .optim-metrics small { color: var(--text-muted); font-size: 11px; }
      .tour-execution-detail .visit-meta { font-size: 12px; color: var(--text-muted); line-height: 1.65; margin: 12px 0; }
      .tour-execution-detail .selected-route { background: var(--primary-soft); }
      .tour-execution-detail .selected-route td:first-child { color: var(--primary-hover); font-weight: 650; }
      .tour-execution-detail .visit-evidence { border: 1px solid var(--border-color); border-radius: 12px; padding: 18px; margin: 12px 0; background: var(--bg-card); }
      .tour-execution-detail .visit-evidence.handled { border-color: #b8d7c8; }
      .tour-execution-detail .visit-heading { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
      .tour-execution-detail .point-number { display: inline-flex; align-items: center; justify-content: center; width: 30px; height: 30px; border-radius: 8px; background: var(--primary-soft); color: var(--primary-hover); font-weight: 650; flex-shrink: 0; }
      .tour-execution-detail .point-info { display: flex; flex-direction: column; flex: 1; min-width: 140px; }
      .tour-execution-detail .point-info span { color: var(--text-muted); font-size: 12px; }
      .tour-execution-detail .badge-valid, .tour-execution-detail .badge-pending, .tour-execution-detail .badge-issue { display: inline-flex; align-items: center; gap: 5px; padding: 4px 8px; border-radius: 6px; font-size: 11px; line-height: 1.5; }
      .tour-execution-detail .badge-valid { color: var(--primary-hover); background: var(--primary-soft); }
      .tour-execution-detail .badge-pending { color: #63716a; background: #f2f5f3; }
      .tour-execution-detail .badge-issue { color: #874d3b; background: #fbf1ec; }
      .tour-execution-detail .evidence-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(200px, 100%), 1fr)); gap: 14px; }
      .tour-execution-detail .evidence-grid figure { margin: 0; }
      .tour-execution-detail .evidence-grid figcaption { font-size: 11px; font-weight: 600; color: var(--text-muted); margin-bottom: 7px; }
      .tour-execution-detail .evidence-image { display: block; width: 100%; max-height: 220px; object-fit: contain; border-radius: 8px; background: #f3f6f4; }
      .tour-execution-detail .evidence-zoom-button { border: none; padding: 0; background: none; cursor: zoom-in; width: 100%; }
      .tour-execution-detail .visit-assessments p { font-size: 13px; margin: 8px 0; }
      .tour-execution-detail .visit-review { display: flex; align-items: end; flex-wrap: wrap; gap: 12px; padding-top: 14px; margin-top: 14px; border-top: 1px solid var(--border-color); }
      .tour-execution-detail .visit-review label { display: flex; flex-direction: column; gap: 6px; font-size: 12px; flex: 1; min-width: min(180px, 100%); }
      .tour-execution-detail .visit-review textarea { width: 100%; border: 1px solid var(--border-color); border-radius: 7px; padding: 9px; }
      .tour-execution-detail .image-zoom-overlay { position: fixed; inset: 0; z-index: 9999; background: #14242ee8; display: flex; align-items: center; justify-content: center; padding: 32px; }
      .tour-execution-detail .zoomed-img { max-width: 94vw; max-height: 88vh; object-fit: contain; }
      .tour-execution-detail .close-zoom-btn { position: absolute; right: 20px; top: 20px; background: white; padding: 8px; border-radius: 8px; border: none; cursor: pointer; }
      @media (max-width: 900px) {
        .tour-execution-detail .info-grid-details, .tour-execution-detail .optim-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
      }
      @media (max-width: 540px) {
        .tour-execution-detail .modal-header { flex-wrap: wrap; }
        .tour-execution-detail .tour-detail-heading { flex-basis: calc(100% - 50px); }
        .tour-execution-detail .tour-detail-header-actions { width: 100%; justify-content: space-between; }
        .tour-execution-detail .info-grid-details, .tour-execution-detail .optim-metrics { grid-template-columns: 1fr; }
        .tour-execution-detail .optim-card, .tour-execution-detail .visit-evidence { padding: 14px; }
        .tour-execution-detail .optim-header button { width: 100%; justify-content: center; }
      }
    `}</style>
  </div>;
}
