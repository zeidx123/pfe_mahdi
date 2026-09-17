import { useState, useEffect } from 'react';
import { FiX } from 'react-icons/fi';
import toast from 'react-hot-toast';
import { MapContainer, TileLayer, Marker, useMapEvents, useMap } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';

const TYPE_DECHET_OPTIONS = [
  { value: 'GENERAL', label: '🗑️ Général', color: '#64748b' },
  { value: 'PLASTIQUE', label: '♻️ Plastique', color: '#3b82f6' },
  { value: 'VERRE', label: '🫙 Verre', color: '#10b981' },
  { value: 'PAPIER', label: '📄 Papier', color: '#f59e0b' },
  { value: 'ORGANIQUE', label: '🌱 Organique', color: '#84cc16' },
];

// Gestionnaire de clic sur la carte dans le modal
const ModalMapClickHandler = ({ onMapClick }) => {
  useMapEvents({
    click: (e) => {
      onMapClick(e.latlng.lat, e.latlng.lng);
    }
  });
  return null;
};

// Gestionnaire de centrage de la carte dans le modal
const MapCenterUpdater = ({ center }) => {
  const map = useMap();
  useEffect(() => {
    if (center) {
      map.setView(center, map.getZoom());
    }
  }, [center, map]);
  return null;
};

const PointCollecteFormModal = ({ isOpen, onClose, onSubmit, point = null, isLoading = false, pendingCoords = null }) => {
  const [formData, setFormData] = useState({
    reference: '',
    adresse: '',
    zone: '',
    latitude: '',
    longitude: '',
    typeDechet: 'GENERAL',
    actif: true
  });

  const [mapCenter, setMapCenter] = useState([36.8065, 10.1815]); // Tunis par défaut

  useEffect(() => {
    if (point) {
      const lat = point.latitude || '';
      const lng = point.longitude || '';
      setFormData({
        reference: point.reference || '',
        adresse: point.adresse || '',
        zone: point.zone || '',
        latitude: lat,
        longitude: lng,
        typeDechet: point.typeDechet || 'GENERAL',
        actif: point.actif !== undefined ? point.actif : true
      });
      if (lat && lng) {
        setMapCenter([parseFloat(lat), parseFloat(lng)]);
      }
    } else if (pendingCoords) {
      const lat = pendingCoords.lat;
      const lng = pendingCoords.lng;
      setFormData(prev => ({
        ...prev,
        latitude: lat.toFixed(6),
        longitude: lng.toFixed(6),
        reference: '', adresse: '', zone: '', typeDechet: 'GENERAL', actif: true
      }));
      setMapCenter([lat, lng]);
    } else {
      setFormData({ reference: '', adresse: '', zone: '', latitude: '', longitude: '', typeDechet: 'GENERAL', actif: true });
      // Tenter de récupérer la position de l'utilisateur pour centrer la carte
      if ('geolocation' in navigator) {
        navigator.geolocation.getCurrentPosition((pos) => {
          setMapCenter([pos.coords.latitude, pos.coords.longitude]);
        });
      }
    }
  }, [point, isOpen, pendingCoords]);

  if (!isOpen) return null;

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData({ ...formData, [name]: type === 'checkbox' ? checked : value });
    
    // Si la latitude ou longitude est changée à la main, recentrer la carte
    if (name === 'latitude' && value) {
      const lngVal = parseFloat(formData.longitude);
      if (!isNaN(lngVal)) setMapCenter([parseFloat(value), lngVal]);
    }
    if (name === 'longitude' && value) {
      const latVal = parseFloat(formData.latitude);
      if (!isNaN(latVal)) setMapCenter([latVal, parseFloat(value)]);
    }
  };

  const handleMapClick = (lat, lng) => {
    setFormData(prev => ({
      ...prev,
      latitude: lat.toFixed(6),
      longitude: lng.toFixed(6)
    }));
    setMapCenter([lat, lng]);
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!formData.reference || !formData.adresse || !formData.latitude || !formData.longitude) {
      toast.error('Veuillez remplir tous les champs obligatoires (référence, adresse, coordonnées)');
      return;
    }
    onSubmit({
      ...formData,
      latitude: parseFloat(formData.latitude),
      longitude: parseFloat(formData.longitude),
    });
  };

  const hasCoords = formData.latitude && formData.longitude;
  const markerPosition = hasCoords ? [parseFloat(formData.latitude), parseFloat(formData.longitude)] : null;

  return (
    <div className="modal-overlay">
      <div className="modal-content large-modal">
        <div className="modal-header">
          <h2>{point ? 'Modifier le point de collecte' : 'Nouveau point de collecte'}</h2>
          <button className="close-btn" onClick={onClose}><FiX /></button>
        </div>
        <form onSubmit={handleSubmit}>
          <div className="modal-body modal-split-layout">
            
            {/* Colonne gauche : Formulaire */}
            <div className="form-column form-grid">
              <div className="form-group">
                <label>Référence * <small>(ex: B-001)</small></label>
                <input type="text" name="reference" value={formData.reference} onChange={handleChange} required placeholder="B-001" />
              </div>
              <div className="form-group">
                <label>Zone / Quartier</label>
                <input type="text" name="zone" value={formData.zone} onChange={handleChange} placeholder="Centre-Ville" />
              </div>
              <div className="form-group full-width">
                <label>Adresse complète *</label>
                <input type="text" name="adresse" value={formData.adresse} onChange={handleChange} required placeholder="Rue, numéro, ville" />
              </div>
              
              <div className="form-group">
                <label>Latitude *</label>
                <input type="number" step="any" name="latitude" value={formData.latitude} onChange={handleChange} required placeholder="33.589886" />
              </div>
              <div className="form-group">
                <label>Longitude *</label>
                <input type="number" step="any" name="longitude" value={formData.longitude} onChange={handleChange} required placeholder="-7.603869" />
              </div>

              <div className="form-group full-width">
                <label>Type de déchet</label>
                <div className="type-grid">
                  {TYPE_DECHET_OPTIONS.map(opt => (
                    <label key={opt.value} className={`type-option ${formData.typeDechet === opt.value ? 'selected' : ''}`}>
                      <input type="radio" name="typeDechet" value={opt.value} checked={formData.typeDechet === opt.value} onChange={handleChange} style={{display:'none'}} />
                      {opt.label}
                    </label>
                  ))}
                </div>
              </div>
              
              <div className="form-group full-width" style={{display:'flex',alignItems:'center',gap:'10px'}}>
                <input type="checkbox" id="actifPoint" name="actif" checked={formData.actif} onChange={handleChange} style={{width:'auto'}} />
                <label htmlFor="actifPoint" style={{marginBottom:0}}>Point actif (visible sur les tournées)</label>
              </div>
            </div>

            {/* Colonne droite : Carte interactive */}
            <div className="map-column">
              <label style={{display:'block', marginBottom: '8px', fontWeight: 600}}>Position sur la carte (cliquez pour placer le point) :</label>
              <div className="modal-map-wrapper">
                <MapContainer
                  center={mapCenter}
                  zoom={14}
                  style={{ width: '100%', height: '100%' }}
                >
                  <TileLayer
                    attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                    url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                  />
                  <MapCenterUpdater center={mapCenter} />
                  <ModalMapClickHandler onMapClick={handleMapClick} />
                  {markerPosition && (
                    <Marker position={markerPosition} />
                  )}
                </MapContainer>
              </div>
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
        .large-modal { max-width: 900px; width: 90%; }
        .modal-split-layout { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; padding: 20px; }
        .modal-map-wrapper { height: 320px; border-radius: 12px; overflow: hidden; border: 2px solid var(--border-color, #e2e8f0); position: relative; }
        .hint { color: var(--text-muted); font-size: 13px; margin-top: -8px; }
        .type-grid { display: flex; gap: 8px; flex-wrap: wrap; }
        .type-option { padding: 6px 12px; border: 2px solid var(--border-color, #e2e8f0); border-radius: 20px; cursor: pointer; font-size: 13px; transition: all 0.2s; }
        .type-option:hover { border-color: var(--primary, #166534); }
        .type-option.selected { border-color: var(--primary, #166534); background: #f0fdf4; color: var(--primary, #166534); font-weight: 600; }
        
        @media (max-width: 768px) {
          .modal-split-layout { grid-template-columns: 1fr; }
          .large-modal { max-width: 500px; }
        }
      `}</style>
    </div>
  );
};

export default PointCollecteFormModal;
