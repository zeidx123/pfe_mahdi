import { useEffect, useState } from 'react';
import { MapContainer, TileLayer, Polyline, CircleMarker, Popup, useMap } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import { FiX, FiMapPin, FiCalendar, FiTruck, FiUser, FiCamera, FiCheck, FiAlertTriangle, FiRefreshCw, FiFileText } from 'react-icons/fi';
import { tourneeService } from '../../services/tourneeService';
import { rapportService } from '../../services/rapportService';
import ProtectedImage from '../../components/ProtectedImage';
import toast from 'react-hot-toast';

const assessmentLabels = {
  CLEANED: 'Le point apparaît nettoyé', NOT_CLEANED: 'Le point ne paraît pas nettoyé',
  OBSTRUCTED: 'Point obstrué', CANNOT_DETERMINE: 'Impossible à déterminer', ANALYSIS_UNAVAILABLE: 'Analyse indisponible',
};
const visitLabels = { PENDING: 'À visiter', ARRIVED: 'Arrivé', COMPLETED_REPORTED: 'Collecte déclarée', ISSUE_REPORTED: 'Problème signalé' };
const algorithmLabels = { MANUAL: 'Ordre manuel', NN: 'Plus proche voisin', NN_2OPT: 'Plus proche voisin + 2-opt', EXACT: 'Exact (Held–Karp)' };
const seconds = value => Number.isFinite(value) ? `${(value / 60).toFixed(1)} min` : 'Non mesuré';
const kilometers = value => Number.isFinite(value) ? `${(value / 1000).toFixed(2)} km` : 'Non disponible';
const when = value => value ? new Date(value).toLocaleString('fr-FR') : 'Non enregistré';
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
    {geometry.length > 0 && <Polyline positions={geometry} pathOptions={{ color: '#0369a1', weight: 4 }} />}
    {depot && <CircleMarker center={depot} radius={9} pathOptions={{ color: '#4338ca', fillOpacity: 1 }}><Popup>{route.depot?.label || 'Dépôt'} · départ et retour</Popup></CircleMarker>}
    {points.map((point, i) => coordinate(point) && <CircleMarker key={point.id} center={coordinate(point)} radius={7} pathOptions={{ color: '#059669', fillOpacity: 1 }}><Popup>{i + 1}. {point.reference}<br />{point.adresse}</Popup></CircleMarker>)}
  </MapContainer>;
}

function VisitEvidence({ tourId, point, visit, onRefresh, onZoom }) {
  const [label, setLabel] = useState(visit.reviewLabel || '');
  const [notes, setNotes] = useState(visit.reviewNotes || '');
  const [saving, setSaving] = useState(false);
  const historyMissing = Boolean(visit.legacyProgressMissing);
  const handled = !historyMissing && ['COMPLETED_REPORTED', 'ISSUE_REPORTED'].includes(visit.status);
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
      <span className={visit.status === 'ISSUE_REPORTED' ? 'badge-pending' : handled ? 'badge-valid' : 'badge-pending'}>
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
    <div className="modal-content modal-lg tour-execution-detail" role="dialog" aria-modal="true" aria-labelledby="tour-detail-title">
      <div className="modal-header">
        <h2 id="tour-detail-title">Tournée {tournee.reference}</h2>
        <button className="btn-secondary" onClick={handleDownloadPdf}><FiFileText /> Rapport PDF</button>
        <button className="close-btn" onClick={onClose} aria-label="Fermer les détails"><FiX /></button>
      </div>
      <div className="modal-body" style={{ maxHeight: 'calc(100vh - 160px)', overflowY: 'auto' }}>
        <div className="info-grid-details">
          <div className="info-card"><FiCalendar /><div><span>Date prévue</span><strong>{tournee.datePrevue} {tournee.heureDepartPrevue || ''}</strong></div></div>
          <div className="info-card"><FiTruck /><div><span>Camion</span><strong>{tournee.camionMatricule || 'Non affecté'}</strong></div></div>
          <div className="info-card"><FiUser /><div><span>Chauffeur</span><strong>{tournee.chauffeurNomComplet || 'Non affecté'}</strong></div></div>
          <div className="info-card"><FiMapPin /><div><span>{tournee.statut || 'Statut inconnu'}</span><strong>{historyMissing ? `${completed} visite(s) confirmée(s) · historique incomplet` : `${completed} / ${visits.length} points traités`}</strong></div></div>
        </div>
        {historyMissing && <p className="notes-box">Le suivi de certains points n’a pas été enregistré dans cette ancienne tournée. Leur avancement historique reste inconnu ; les photos historiques sans étape précisée ne sont pas considérées comme des photos APRÈS.</p>}
        {tournee.description && <p className="notes-box">{tournee.description}</p>}
        <section className="optim-card">
          <div className="optim-header"><h3>Parcours routier — départ et retour au dépôt</h3>
            <button className="btn-primary" onClick={handleReoptimiser} disabled={reoptimising || tournee.statut !== 'PLANIFIEE'}><FiRefreshCw /> {reoptimising ? 'Calcul…' : 'Calculer / comparer'}</button>
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
      {zoomedImage && <div className="image-zoom-overlay" role="dialog" aria-label="Photo de collecte agrandie" onClick={() => setZoomedImage(null)}>
        <button className="close-zoom-btn" onClick={() => setZoomedImage(null)} aria-label="Fermer la photo"><FiX /></button>
        <ProtectedImage path={zoomedImage} alt="Preuve de collecte agrandie" className="zoomed-img" />
      </div>}
    </div>
    <style>{`
      .tour-execution-detail { max-width: 1050px; }
      .tour-execution-detail h3 { font-size: 16px; margin: 0 0 12px; }
      .tour-execution-detail section { margin: 20px 0; }
      .info-grid-details { display: grid; grid-template-columns: repeat(auto-fit,minmax(180px,1fr)); gap: 12px; }
      .info-card { display: flex; align-items: center; gap: 10px; padding: 12px; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px; }
      .info-card svg { color: #0369a1; }.info-card span { display: block; font-size: 12px; color: #64748b; }
      .notes-box { background: #fffbeb; border-left: 4px solid #f59e0b; border-radius: 6px; padding: 12px; }
      .optim-card { background: #eff6ff; border: 1px solid #bfdbfe; padding: 16px; border-radius: 12px; }
      .optim-header { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; }
      .optim-header button { display: flex; align-items: center; gap: 6px; }
      .optim-metrics { display: grid; grid-template-columns: repeat(auto-fit,minmax(150px,1fr)); gap: 10px; margin: 12px 0; }
      .optim-metrics > div { background: white; padding: 12px; border: 1px solid #e2e8f0; border-radius: 8px; }
      .optim-metrics span { display: block; color: #64748b; font-size: 12px; }.optim-metrics strong { display: block; font-size: 18px; margin-top: 4px; }
      .visit-meta { font-size: 12px; color: #64748b; line-height: 1.5; margin: 10px 0; }
      .selected-route { background: #dbeafe; }.visit-evidence { border: 1px solid #e2e8f0; border-radius: 12px; padding: 16px; margin: 12px 0; }
      .visit-evidence.handled { border-color: #a7f3d0; }.visit-heading { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
      .point-number { display: inline-flex; align-items: center; justify-content: center; width: 28px; height: 28px; border-radius: 50%; background: #e0f2fe; }
      .point-info { display: flex; flex-direction: column; flex: 1; }.point-info span { color: #64748b; font-size: 12px; }
      .badge-valid,.badge-pending { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; }.badge-valid { color: #166534; }.badge-pending { color: #92400e; }
      .evidence-grid { display: grid; grid-template-columns: repeat(auto-fit,minmax(200px,1fr)); gap: 14px; }.evidence-grid figure { margin: 0; }.evidence-grid figcaption { font-size: 12px; margin-bottom: 6px; }
      .evidence-image { display: block; width: 100%; max-height: 220px; object-fit: contain; border-radius: 8px; background: #f1f5f9; }
      .evidence-zoom-button { border: none; padding: 0; background: none; cursor: zoom-in; width: 100%; }
      .visit-assessments p { font-size: 13px; margin: 7px 0; }.visit-review { display: flex; align-items: end; flex-wrap: wrap; gap: 12px; padding-top: 12px; border-top: 1px solid #e2e8f0; }
      .visit-review label { display: flex; flex-direction: column; gap: 5px; font-size: 12px; flex: 1; min-width: 180px; }.visit-review textarea { width: 100%; border: 1px solid #cbd5e1; border-radius: 6px; padding: 8px; }
      .image-zoom-overlay { position: fixed; inset: 0; z-index: 9999; background: rgba(0,0,0,.85); display: flex; align-items: center; justify-content: center; padding: 32px; }
      .zoomed-img { max-width: 94vw; max-height: 88vh; object-fit: contain; }.close-zoom-btn { position: absolute; right: 20px; top: 20px; background: white; padding: 8px; border-radius: 8px; border: none; cursor: pointer; }
    `}</style>
  </div>;
}
