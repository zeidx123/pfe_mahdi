import { useState, useEffect } from 'react';
import { MapContainer, TileLayer, Marker, Popup, useMapEvents, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { FiPlus, FiEdit2, FiTrash2, FiSearch, FiMapPin } from 'react-icons/fi';
import toast from 'react-hot-toast';
import { pointCollecteService } from '../../services/pointCollecteService';
import PointCollecteFormModal from './PointCollecteFormModal';

// Fix Leaflet default icon issue with Vite
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png',
});

const TYPE_COLORS = {
  GENERAL: '#64748b',
  PLASTIQUE: '#3b82f6',
  VERRE: '#10b981',
  PAPIER: '#f59e0b',
  ORGANIQUE: '#84cc16',
};

const TYPE_LABELS = {
  GENERAL: '🗑️ Général',
  PLASTIQUE: '♻️ Plastique',
  VERRE: '🫙 Verre',
  PAPIER: '📄 Papier',
  ORGANIQUE: '🌱 Organique',
};

const createCustomIcon = (typeDechet, niveauRemplissage = 0) => {
  const color = TYPE_COLORS[typeDechet] || '#64748b';
  const fill = niveauRemplissage > 75 ? '#ef4444' : niveauRemplissage > 50 ? '#f59e0b' : '#22c55e';
  return L.divIcon({
    className: '',
    html: `
      <div style="position:relative;width:36px;height:44px;">
        <svg width="36" height="44" viewBox="0 0 36 44" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M18 0C8.06 0 0 8.06 0 18C0 31.5 18 44 18 44C18 44 36 31.5 36 18C36 8.06 27.94 0 18 0Z" fill="${color}"/>
          <circle cx="18" cy="18" r="10" fill="white"/>
          <text x="18" y="23" text-anchor="middle" font-size="12" fill="${color}">🗑</text>
        </svg>
        <div style="position:absolute;bottom:-6px;right:-6px;width:18px;height:18px;background:${fill};border:2px solid white;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:8px;font-weight:bold;color:white;">${niveauRemplissage}%</div>
      </div>`,
    iconSize: [36, 44],
    iconAnchor: [18, 44],
    popupAnchor: [0, -44],
  });
};

const MapClickHandler = ({ onMapClick }) => {
  useMapEvents({ click: (e) => onMapClick(e.latlng) });
  return null;
};

const MapCenterUpdater = ({ center }) => {
  const map = useMap();
  useEffect(() => {
    if (center) {
      map.flyTo(center, 14);
    }
  }, [center, map]);
  return null;
};

const PointCollectePage = () => {
  const [points, setPoints] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedPoint, setSelectedPoint] = useState(null);
  const [pendingCoords, setPendingCoords] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [mapCenter, setMapCenter] = useState([33.5731, -7.5898]); // Casablanca par défaut

  useEffect(() => { 
    fetchPoints(); 
    
    // Demander la position actuelle
    if ('geolocation' in navigator) {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          setMapCenter([position.coords.latitude, position.coords.longitude]);
        },
        (error) => {
          console.log("Erreur géolocalisation:", error);
        }
      );
    }
  }, []);

  const fetchPoints = async () => {
    try {
      setLoading(true);
      const data = await pointCollecteService.getAll();
      setPoints(data);
    } catch (error) {
      toast.error('Erreur lors du chargement des points de collecte');
    } finally {
      setLoading(false);
    }
  };

  const handleMapClick = (latlng) => {
    setPendingCoords(latlng);
    setSelectedPoint(null);
    setIsModalOpen(true);
  };

  const handleOpenModal = (point = null) => {
    setSelectedPoint(point);
    setPendingCoords(null);
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setSelectedPoint(null);
    setPendingCoords(null);
    setIsModalOpen(false);
  };

  const handleSubmit = async (formData) => {
    try {
      setIsSubmitting(true);
      if (selectedPoint) {
        await pointCollecteService.update(selectedPoint.id, formData);
        toast.success('Point mis à jour avec succès');
      } else {
        await pointCollecteService.create(formData);
        toast.success('Point de collecte ajouté sur la carte !');
      }
      handleCloseModal();
      fetchPoints();
    } catch (error) {
      toast.error(error.response?.data?.message || 'Erreur lors de l\'enregistrement');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDelete = async (id) => {
    if (window.confirm('Voulez-vous vraiment désactiver ce point de collecte ?')) {
      try {
        await pointCollecteService.delete(id);
        toast.success('Point désactivé');
        fetchPoints();
      } catch {
        toast.error('Erreur lors de la suppression');
      }
    }
  };

  const filteredPoints = points.filter(p =>
    p.reference.toLowerCase().includes(searchTerm.toLowerCase()) ||
    p.adresse.toLowerCase().includes(searchTerm.toLowerCase()) ||
    (p.zone && p.zone.toLowerCase().includes(searchTerm.toLowerCase()))
  );

  const activePoints = points.filter(p => p.actif);

  return (
    <div className="points-page">
      <div className="page-header-row">
        <div>
          <h1>Points de Collecte</h1>
          <p>Gérez les points de collecte et visualisez-les sur la carte. <strong>Cliquez sur la carte</strong> pour ajouter un nouveau point.</p>
        </div>
        <button className="btn-primary" onClick={() => handleOpenModal()}>
          <FiPlus style={{ marginRight: '8px' }} /> Nouveau Point
        </button>
      </div>

      <div className="map-layout">
        {/* LEFT: List */}
        <div className="points-list-panel">
          <div className="search-bar">
            <FiSearch className="search-icon" />
            <input
              type="text"
              placeholder="Rechercher par référence, adresse..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>
          <div className="points-count">{filteredPoints.length} point(s) affiché(s)</div>
          <div className="points-list-scroll">
            {loading ? (
              <div className="empty-state">Chargement...</div>
            ) : filteredPoints.length === 0 ? (
              <div className="empty-state">Aucun point trouvé.</div>
            ) : (
              filteredPoints.map(point => (
                <div key={point.id} className={`point-card ${!point.actif ? 'inactive' : ''}`}>
                  <div className="point-card-header">
                    <span className="point-ref">{point.reference}</span>
                    <span className="point-type" style={{ background: TYPE_COLORS[point.typeDechet] + '20', color: TYPE_COLORS[point.typeDechet] }}>
                      {TYPE_LABELS[point.typeDechet]}
                    </span>
                  </div>
                  <p className="point-adresse"><FiMapPin style={{ marginRight: 4 }} />{point.adresse}</p>
                  {point.zone && <p className="point-zone">Zone : {point.zone}</p>}
                  <div className="point-fill-bar">
                    <span>Remplissage :</span>
                    <div className="fill-track">
                      <div
                        className="fill-fill"
                        style={{
                          width: `${point.niveauRemplissage}%`,
                          background: point.niveauRemplissage > 75 ? '#ef4444' : point.niveauRemplissage > 50 ? '#f59e0b' : '#22c55e'
                        }}
                      />
                    </div>
                    <span className="fill-pct">{point.niveauRemplissage}%</span>
                  </div>
                  <div className="point-actions">
                    <button className="icon-btn edit" onClick={() => handleOpenModal(point)} title="Modifier"><FiEdit2 /></button>
                    <button className="icon-btn delete" onClick={() => handleDelete(point.id)} title="Désactiver" disabled={!point.actif}><FiTrash2 /></button>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        {/* RIGHT: Map */}
        <div className="map-panel">
          <div className="map-tip">💡 Cliquez sur la carte pour ajouter un point</div>
          <MapContainer
            center={mapCenter}
            zoom={13}
            style={{ width: '100%', height: '100%' }}
          >
            <MapCenterUpdater center={mapCenter} />
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <MapClickHandler onMapClick={handleMapClick} />
            {activePoints.map(point => (
              point.latitude && point.longitude ? (
                <Marker
                  key={point.id}
                  position={[point.latitude, point.longitude]}
                  icon={createCustomIcon(point.typeDechet, point.niveauRemplissage)}
                >
                  <Popup>
                    <div style={{ minWidth: 180 }}>
                      <strong style={{ fontSize: 15 }}>{point.reference}</strong>
                      <p style={{ margin: '4px 0', color: '#64748b', fontSize: 13 }}>{point.adresse}</p>
                      {point.zone && <p style={{ margin: '2px 0', fontSize: 12 }}>Zone : {point.zone}</p>}
                      <p style={{ margin: '4px 0', fontSize: 12 }}>Type : {TYPE_LABELS[point.typeDechet]}</p>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 6 }}>
                        <span style={{ fontSize: 12 }}>Remplissage :</span>
                        <strong style={{ color: point.niveauRemplissage > 75 ? '#ef4444' : point.niveauRemplissage > 50 ? '#f59e0b' : '#22c55e' }}>
                          {point.niveauRemplissage}%
                        </strong>
                      </div>
                      <button
                        onClick={() => handleOpenModal(point)}
                        style={{ marginTop: 8, padding: '4px 10px', background: 'var(--primary, #166534)', color: 'white', border: 'none', borderRadius: 6, cursor: 'pointer', fontSize: 12 }}
                      >
                        Modifier
                      </button>
                    </div>
                  </Popup>
                </Marker>
              ) : null
            ))}
          </MapContainer>
        </div>
      </div>

      <PointCollecteFormModal
        isOpen={isModalOpen}
        onClose={handleCloseModal}
        onSubmit={handleSubmit}
        point={selectedPoint}
        isLoading={isSubmitting}
        pendingCoords={pendingCoords}
      />

      <style>{`
        .points-page { display: flex; flex-direction: column; height: 100%; }
        .page-header-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1.5rem; }
        .map-layout { display: grid; grid-template-columns: 360px 1fr; gap: 1.5rem; flex: 1; min-height: 600px; }
        .points-list-panel { background: var(--bg-card); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); display: flex; flex-direction: column; overflow: hidden; }
        .search-bar { position: relative; padding: 1rem; border-bottom: 1px solid var(--border-color); }
        .search-icon { position: absolute; left: 24px; top: 50%; transform: translateY(-50%); color: var(--text-muted); }
        .search-bar input { width: 100%; padding: 0.6rem 1rem 0.6rem 2.5rem; border: 1px solid var(--border-color); border-radius: var(--radius-md); }
        .points-count { padding: 0.5rem 1rem; font-size: 12px; color: var(--text-muted); border-bottom: 1px solid var(--border-color); }
        .points-list-scroll { flex: 1; overflow-y: auto; padding: 0.75rem; display: flex; flex-direction: column; gap: 0.75rem; }
        .point-card { background: #f8fafc; border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 1rem; transition: box-shadow 0.2s; }
        .point-card:hover { box-shadow: var(--shadow-md); }
        .point-card.inactive { opacity: 0.5; }
        .point-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
        .point-ref { font-weight: 700; font-size: 15px; }
        .point-type { font-size: 11px; padding: 2px 8px; border-radius: 10px; font-weight: 600; }
        .point-adresse { font-size: 13px; color: var(--text-muted); margin: 0 0 4px; display: flex; align-items: center; }
        .point-zone { font-size: 12px; color: var(--text-muted); margin: 0 0 8px; }
        .point-fill-bar { display: flex; align-items: center; gap: 8px; font-size: 12px; margin-bottom: 10px; }
        .fill-track { flex: 1; background: #e2e8f0; border-radius: 4px; height: 6px; }
        .fill-fill { height: 100%; border-radius: 4px; transition: width 0.4s; }
        .fill-pct { font-weight: 700; min-width: 32px; text-align: right; }
        .point-actions { display: flex; gap: 8px; justify-content: flex-end; }
        .map-panel { border-radius: var(--radius-lg); overflow: hidden; box-shadow: var(--shadow-sm); position: relative; }
        .map-tip { position: absolute; top: 12px; left: 50%; transform: translateX(-50%); background: white; padding: 6px 14px; border-radius: 20px; box-shadow: var(--shadow-md); font-size: 13px; z-index: 1000; pointer-events: none; white-space: nowrap; }
        .icon-btn { padding: 6px; border-radius: 6px; display: flex; align-items: center; justify-content: center; color: var(--text-muted); border: none; background: transparent; cursor: pointer; }
        .icon-btn:hover { background: #f1f5f9; color: var(--primary); }
        .icon-btn.delete:hover:not(:disabled) { background: #fee2e2; color: #ef4444; }
        .icon-btn:disabled { opacity: 0.3; cursor: not-allowed; }
        .empty-state { padding: 2rem; text-align: center; color: var(--text-muted); }
      `}</style>
    </div>
  );
};

export default PointCollectePage;
