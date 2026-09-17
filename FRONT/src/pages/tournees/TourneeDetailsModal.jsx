import React, { useState } from 'react';
import { FiX, FiMapPin, FiCalendar, FiTruck, FiUser, FiCamera, FiCheck, FiAlertTriangle, FiNavigation, FiClock, FiRefreshCw, FiFileText } from 'react-icons/fi';
import { tourneeService } from '../../services/tourneeService';
import { rapportService } from '../../services/rapportService';
import toast from 'react-hot-toast';

const TourneeDetailsModal = ({ isOpen, onClose, tournee, onRefresh }) => {
  const [zoomedImage, setZoomedImage] = useState(null);
  const [reoptimising, setReoptimising] = useState(false);

  if (!isOpen || !tournee) return null;

  const photoBaseUrl = 'http://localhost:8080/uploads/photos/';
  const points = tournee.pointsCollecte || [];
  const photosPoints = tournee.photosPoints || {};

  const hasOptimisation = tournee.distanceOptimaleKm != null && tournee.distanceOptimaleKm > 0;

  const handleDownloadPdf = async () => {
    try {
      await rapportService.downloadTourneePdf(tournee.id, tournee.reference);
      toast.success('Rapport PDF téléchargé avec succès');
    } catch (e) {
      toast.error('Erreur lors du téléchargement du PDF');
    }
  };

  const handleReoptimiser = async () => {
    try {
      setReoptimising(true);
      await tourneeService.reoptimiser(tournee.id);
      toast.success('Optimisation IA recalculée avec succès !');
      if (onRefresh) onRefresh();
    } catch (e) {
      toast.error("Erreur lors de la ré-optimisation.");
    } finally {
      setReoptimising(false);
    }
  };

  return (
    <div className="modal-overlay">
      <div className="modal-content modal-lg">
        <div className="modal-header bg-primary text-white" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <h2>Détails de la Tournée {tournee.reference}</h2>
            <button
              onClick={handleDownloadPdf}
              style={{ background: 'white', color: '#0369a1', border: 'none', padding: '6px 12px', borderRadius: '6px', fontSize: '12px', fontWeight: 'bold', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '6px' }}
              title="Télécharger le rapport PDF de la tournée"
            >
              <FiFileText /> Rapport PDF
            </button>
          </div>
          <button className="close-btn text-white" onClick={onClose}><FiX /></button>
        </div>

        <div className="modal-body" style={{ maxHeight: 'calc(100vh - 200px)', overflowY: 'auto' }}>
          {/* General Stats / Infos */}
          <div className="info-grid-details mb-4">
            <div className="info-card">
              <FiCalendar className="info-icon" />
              <div>
                <span className="label">Date Prévue</span>
                <strong>{tournee.datePrevue} {tournee.heureDepartPrevue ? `à ${tournee.heureDepartPrevue}` : ''}</strong>
              </div>
            </div>
            <div className="info-card">
              <FiTruck className="info-icon" />
              <div>
                <span className="label">Camion</span>
                <strong>{tournee.camionMatricule || 'Non affecté'}</strong>
              </div>
            </div>
            <div className="info-card">
              <FiUser className="info-icon" />
              <div>
                <span className="label">Chauffeur</span>
                <strong>{tournee.chauffeurNomComplet || 'Non affecté'}</strong>
              </div>
            </div>
            <div className="info-card">
              <span className="info-icon">●</span>
              <div>
                <span className="label">Statut</span>
                <span className={`status-badge-details status-${tournee.statut.toLowerCase()}`}>
                  {tournee.statut}
                </span>
              </div>
            </div>
          </div>

          {/* Description & Notes */}
          {tournee.description && (
            <div className="notes-box mb-4">
              <strong>Description :</strong> {tournee.description}
            </div>
          )}

          {/* ===== CARTE OPTIMISATION IA ===== */}
          <div className="optim-card mb-4">
            <div className="optim-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ fontSize: 18 }}>🤖</span>
                <span style={{ fontWeight: 700, fontSize: 14, color: '#1e40af' }}>Optimisation IA — Chemin le plus court</span>
                {tournee.optimisationEffectuee && (
                  <span className="optim-badge-ok">✓ Optimisé</span>
                )}
                {!tournee.optimisationEffectuee && (
                  <span className="optim-badge-warn">⚠ GPS manquant</span>
                )}
              </div>
              <button
                className="optim-refresh-btn"
                onClick={handleReoptimiser}
                disabled={reoptimising}
                title="Recalculer l'optimisation IA"
              >
                <FiRefreshCw style={{ marginRight: 4, animation: reoptimising ? 'spin 1s linear infinite' : 'none' }} />
                {reoptimising ? 'Calcul...' : 'Recalculer'}
              </button>
            </div>

            <div className="optim-stats">
              {/* Ordre des points */}
              <div className="optim-stat-full">
                <FiNavigation style={{ color: '#2563eb', marginRight: 6 }} />
                <span className="optim-label">Ordre optimisé :</span>
                <div className="optim-route">
                  {(tournee.pointsAdressesOrdonnes || points.map(p => p.reference)).map((addr, idx) => (
                    <span key={idx} className="optim-stop">
                      {idx > 0 && <span className="optim-arrow">→</span>}
                      <span className="optim-stop-label">{addr.split(' – ')[0]}</span>
                    </span>
                  ))}
                  {(!tournee.pointsAdressesOrdonnes || tournee.pointsAdressesOrdonnes.length === 0) && (
                    <span style={{ color: '#94a3b8', fontSize: 13 }}>Aucun point défini</span>
                  )}
                </div>
              </div>

              {/* Distance + Durée */}
              <div className="optim-metrics">
                <div className="optim-metric">
                  <span className="optim-metric-icon">📏</span>
                  <div>
                    <span className="optim-metric-label">Distance totale</span>
                    <span className="optim-metric-value">
                      {hasOptimisation ? `${tournee.distanceOptimaleKm} km` : '—'}
                    </span>
                  </div>
                </div>
                <div className="optim-metric">
                  <span className="optim-metric-icon">⏱️</span>
                  <div>
                    <span className="optim-metric-label">Durée estimée</span>
                    <span className="optim-metric-value">
                      {tournee.dureeFormatee || '—'}
                    </span>
                  </div>
                </div>
                <div className="optim-metric">
                  <span className="optim-metric-icon">📍</span>
                  <div>
                    <span className="optim-metric-label">Arrêts</span>
                    <span className="optim-metric-value">{points.length} point(s)</span>
                  </div>
                </div>
              </div>

              {/* Explication du choix de l'ordre */}
              {tournee.explicationOrdre && (
                <div className="optim-explication-box">
                  <div className="optim-explication-title">🧠 Justification IA du parcours :</div>
                  <div className="optim-explication-content" style={{ whiteSpace: 'pre-line' }}>
                    {tournee.explicationOrdre.replace(/\*\*/g, '')}
                  </div>
                </div>
              )}

              {!hasOptimisation && (
                <div style={{ fontSize: 12, color: '#b45309', background: '#fffbeb', borderRadius: 8, padding: '8px 12px', marginTop: 8 }}>
                  ⚠️ Ajoutez des coordonnées GPS aux points de collecte pour activer le calcul de trajet optimal.
                </div>
              )}
            </div>
          </div>

          {/* Points list */}
          <h3 className="section-title-details mb-3">
            <FiMapPin style={{ marginRight: 6 }} /> Points de collecte visités ({points.length})
          </h3>

          <div className="points-details-list mb-4">
            {points.length === 0 ? (
              <p className="text-muted">Aucun point de collecte sur cette tournée.</p>
            ) : (
              points.map((p, index) => {
                const photoName = photosPoints[p.id];
                const isValidated = !!photoName;

                return (
                  <div key={p.id} className={`point-detail-row ${isValidated ? 'validated' : ''}`}>
                    <div className="point-number">{index + 1}</div>
                    <div className="point-info">
                      <strong>{p.reference}</strong>
                      <span>{p.adresse}</span>
                    </div>
                    <div className="point-fill">
                      <span className="fill-value">{p.niveauRemplissage}% plein</span>
                      <div className="fill-bar-bg">
                        <div className="fill-bar-fg" style={{ width: `${p.niveauRemplissage}%` }}></div>
                      </div>
                    </div>
                    <div className="point-status-cell">
                      {isValidated ? (
                        <span className="badge-valid"><FiCheck /> Validé</span>
                      ) : (
                        <span className="badge-pending"><FiAlertTriangle /> En attente</span>
                      )}
                    </div>
                    <div className="point-photo-cell">
                      {photoName ? (
                        <div className="photo-thumbnail-container" onClick={() => setZoomedImage(photoBaseUrl + photoName)}>
                          <img src={photoBaseUrl + photoName} alt="Preuve" className="photo-thumbnail" />
                          <div className="thumbnail-overlay">
                            <FiCamera />
                          </div>
                        </div>
                      ) : (
                        <span className="no-photo">-</span>
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>

          {/* Photo de fin de tournée */}
          {tournee.photoFin && (
            <div className="fin-photo-section">
              <h3 className="section-title-details mb-3">
                <FiCamera style={{ marginRight: 6 }} /> Photo de fin de service
              </h3>
              <div className="fin-photo-container" onClick={() => setZoomedImage(photoBaseUrl + tournee.photoFin)}>
                <img src={photoBaseUrl + tournee.photoFin} alt="Fin de service" className="fin-photo-img" />
                <div className="fin-photo-overlay">
                  <FiCamera size={24} />
                  <span>Agrandir la photo de clôture</span>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Modal de zoom d'image */}
        {zoomedImage && (
          <div className="image-zoom-overlay" onClick={() => setZoomedImage(null)}>
            <div className="image-zoom-content">
              <button className="close-zoom-btn" onClick={() => setZoomedImage(null)}><FiX /></button>
              <img src={zoomedImage} alt="Zoom" className="zoomed-img" />
            </div>
          </div>
        )}
      </div>

      <style>{`
        @keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

        .info-grid-details { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 1rem; }
        .info-card { background: #f8fafc; border: 1px solid #e2e8f0; padding: 12px; border-radius: 12px; display: flex; align-items: center; gap: 10px; }
        .info-icon { font-size: 1.25rem; color: var(--primary); }
        .info-card .label { font-size: 11px; color: var(--text-muted); display: block; }
        .status-badge-details { display: inline-block; padding: 2px 8px; border-radius: 8px; font-size: 11px; font-weight: 700; text-transform: uppercase; }
        .status-planifiee { background: #dbeafe; color: #1e40af; }
        .status-en_cours { background: #fef9c3; color: #854d0e; }
        .status-terminee { background: #dcfce7; color: #166534; }
        .status-annulee { background: #fee2e2; color: #991b1b; }

        .notes-box { background: #fffbeb; border-left: 4px solid #f59e0b; padding: 12px; border-radius: 4px 12px 12px 4px; font-size: 13px; }
        .section-title-details { font-size: 15px; font-weight: 700; color: #1e293b; display: flex; align-items: center; border-bottom: 2px solid #e2e8f0; padding-bottom: 6px; }

        /* ===== Optimisation IA Card ===== */
        .optim-card { background: linear-gradient(135deg, #eff6ff 0%, #f0f9ff 100%); border: 1.5px solid #bfdbfe; border-radius: 14px; overflow: hidden; }
        .optim-header { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border-bottom: 1px solid #bfdbfe; background: rgba(255,255,255,0.6); flex-wrap: wrap; gap: 8px; }
        .optim-badge-ok { background: #dcfce7; color: #166534; font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 20px; }
        .optim-badge-warn { background: #fef9c3; color: #854d0e; font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 20px; }
        .optim-refresh-btn { display: flex; align-items: center; gap: 4px; padding: 5px 12px; border-radius: 8px; background: #2563eb; color: white; border: none; cursor: pointer; font-size: 12px; font-weight: 600; transition: background 0.2s; }
        .optim-refresh-btn:hover:not(:disabled) { background: #1d4ed8; }
        .optim-refresh-btn:disabled { opacity: 0.6; cursor: not-allowed; }

        .optim-stats { padding: 14px 16px; display: flex; flex-direction: column; gap: 12px; }
        .optim-stat-full { display: flex; align-items: flex-start; gap: 6px; flex-wrap: wrap; }
        .optim-label { font-size: 12px; font-weight: 700; color: #475569; white-space: nowrap; }
        .optim-route { display: flex; flex-wrap: wrap; align-items: center; gap: 4px; margin-top: 4px; }
        .optim-stop { display: flex; align-items: center; gap: 4px; }
        .optim-arrow { color: #94a3b8; font-size: 12px; }
        .optim-stop-label { background: #dbeafe; color: #1e40af; font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 20px; }

        .optim-metrics { display: flex; gap: 16px; flex-wrap: wrap; }
        .optim-metric { display: flex; align-items: center; gap: 10px; background: white; border: 1px solid #e2e8f0; border-radius: 10px; padding: 10px 14px; flex: 1; min-width: 130px; }
        .optim-metric-icon { font-size: 18px; }
        .optim-metric-label { font-size: 11px; color: #64748b; display: block; margin-bottom: 2px; }
        .optim-metric-value { font-size: 16px; font-weight: 800; color: #1e293b; display: block; }

        .optim-explication-box { background: #ffffff; border: 1px solid #dbeafe; border-left: 4px solid #2563eb; border-radius: 10px; padding: 12px 14px; }
        .optim-explication-title { font-size: 13px; font-weight: 700; color: #1e40af; margin-bottom: 6px; }
        .optim-explication-content { font-size: 12px; color: #334155; line-height: 1.5; }

        .points-details-list { display: flex; flex-direction: column; gap: 8px; }
        .point-detail-row { display: flex; align-items: center; gap: 14px; padding: 12px 16px; background: white; border: 1px solid #e2e8f0; border-radius: 12px; transition: all 0.2s; }
        .point-detail-row.validated { border-color: #bbf7d0; background: #f0fdf4; }
        .point-number { width: 24px; height: 24px; border-radius: 50%; background: #e2e8f0; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 700; color: #475569; }
        .point-detail-row.validated .point-number { background: #22c55e; color: white; }
        .point-info { flex: 2; display: flex; flex-direction: column; }
        .point-info strong { font-size: 13px; color: #1e293b; }
        .point-info span { font-size: 11px; color: var(--text-muted); }
        .point-fill { flex: 1.5; }
        .fill-value { font-size: 11px; font-weight: 600; color: #475569; display: block; margin-bottom: 4px; }
        .fill-bar-bg { width: 100%; height: 6px; background: #e2e8f0; border-radius: 3px; overflow: hidden; }
        .fill-bar-fg { height: 100%; background: #22c55e; border-radius: 3px; }
        .point-status-cell { flex: 1; }
        .badge-valid { display: inline-flex; align-items: center; gap: 4px; color: #166534; font-weight: 600; font-size: 12px; }
        .badge-pending { display: inline-flex; align-items: center; gap: 4px; color: #b45309; font-weight: 600; font-size: 12px; }
        .point-photo-cell { width: 60px; display: flex; justify-content: center; }
        .photo-thumbnail-container { width: 50px; height: 38px; border-radius: 6px; overflow: hidden; position: relative; border: 1px solid #cbd5e1; cursor: pointer; }
        .photo-thumbnail { width: 100%; height: 100%; object-fit: cover; }
        .thumbnail-overlay { position: absolute; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; color: white; opacity: 0; transition: opacity 0.2s; font-size: 12px; }
        .photo-thumbnail-container:hover .thumbnail-overlay { opacity: 1; }
        .no-photo { color: #94a3b8; font-size: 16px; }

        .fin-photo-container { position: relative; border-radius: 12px; overflow: hidden; border: 1px solid #cbd5e1; max-width: 400px; cursor: pointer; }
        .fin-photo-img { width: 100%; max-height: 250px; object-fit: cover; display: block; }
        .fin-photo-overlay { position: absolute; inset: 0; background: rgba(0,0,0,0.3); display: flex; flex-direction: column; align-items: center; justify-content: center; color: white; opacity: 0; transition: opacity 0.2s; gap: 8px; }
        .fin-photo-container:hover .fin-photo-overlay { opacity: 1; }

        .image-zoom-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.8); z-index: 9999; display: flex; align-items: center; justify-content: center; padding: 2rem; cursor: zoom-out; }
        .image-zoom-content { position: relative; max-width: 90%; max-height: 90%; }
        .zoomed-img { max-width: 100%; max-height: 90vh; border-radius: 12px; box-shadow: 0 10px 25px rgba(0,0,0,0.5); border: 2px solid white; }
        .close-zoom-btn { position: absolute; top: -40px; right: 0; background: transparent; border: none; color: white; font-size: 24px; cursor: pointer; }
      `}</style>
    </div>
  );
};

export default TourneeDetailsModal;
