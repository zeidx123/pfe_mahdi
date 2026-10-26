import { useState, useEffect, useRef } from 'react';
import { FiX, FiNavigation } from 'react-icons/fi';
import toast from 'react-hot-toast';
import { MapContainer, TileLayer, Marker, useMapEvents, useMap } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import MapResizeObserver from '../../components/MapResizeObserver';
import { DEFAULT_MAP_CENTER, hasValidCoordinates, getUserLocation } from '../../utils/mapCoordinates';

const TYPE_DECHET_OPTIONS = [
  { value: 'GENERAL', label: 'Général' },
  { value: 'PLASTIQUE', label: 'Plastique' },
  { value: 'VERRE', label: 'Verre' },
  { value: 'PAPIER', label: 'Papier' },
  { value: 'ORGANIQUE', label: 'Organique' },
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

const PointCollecteFormModal = ({ isOpen, onClose, onSubmit, point = null, isLoading = false, pendingCoords = null, defaultCenter = DEFAULT_MAP_CENTER }) => {
  const firstInputRef = useRef(null);
  const [formData, setFormData] = useState({
    reference: '',
    adresse: '',
    zone: '',
    latitude: '',
    longitude: '',
    typeDechet: 'GENERAL',
    actif: true
  });

  const [mapCenter, setMapCenter] = useState(DEFAULT_MAP_CENTER);

  useEffect(() => {
    if (!isOpen) return;
    const fallbackCenter = hasValidCoordinates(defaultCenter?.[0], defaultCenter?.[1]) ? defaultCenter : DEFAULT_MAP_CENTER;
    setMapCenter(fallbackCenter);
    if (point) {
      const lat = point.latitude ?? '';
      const lng = point.longitude ?? '';
      setFormData({
        reference: point.reference || '',
        adresse: point.adresse || '',
        zone: point.zone || '',
        latitude: lat,
        longitude: lng,
        typeDechet: point.typeDechet || 'GENERAL',
        actif: point.actif !== undefined ? point.actif : true
      });
      if (hasValidCoordinates(lat, lng)) {
        setMapCenter([parseFloat(lat), parseFloat(lng)]);
      }
    } else if (pendingCoords && hasValidCoordinates(pendingCoords.lat, pendingCoords.lng)) {
      const lat = Number(pendingCoords.lat);
      const lng = Number(pendingCoords.lng);
      setFormData(prev => ({
        ...prev,
        latitude: lat.toFixed(6),
        longitude: lng.toFixed(6),
        reference: '', adresse: '', zone: '', typeDechet: 'GENERAL', actif: true
      }));
      setMapCenter([lat, lng]);
    } else {
      setFormData({ reference: '', adresse: '', zone: '', latitude: '', longitude: '', typeDechet: 'GENERAL', actif: true });
      // When opening to create a new point, center the map on user location if available
      getUserLocation()
        .then((loc) => {
          setMapCenter(loc);
        })
        .catch(() => {});
    }
  }, [point, isOpen, pendingCoords, defaultCenter]);

  const handleUseMyLocation = async () => {
    try {
      const [lat, lng] = await getUserLocation();
      setFormData(prev => ({
        ...prev,
        latitude: lat.toFixed(6),
        longitude: lng.toFixed(6)
      }));
      setMapCenter([lat, lng]);
      toast.success('Position GPS appliquée');
    } catch {
      toast.error('Impossible d’accéder à votre position GPS');
    }
  };

  useEffect(() => {
    if (!isOpen) return;
    const previousFocus = document.activeElement;
    firstInputRef.current?.focus();
    return () => previousFocus?.focus();
  }, [isOpen]);

  if (!isOpen) return null;

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    const nextData = { ...formData, [name]: type === 'checkbox' ? checked : value };
    setFormData(nextData);

    if ((name === 'latitude' || name === 'longitude') && hasValidCoordinates(nextData.latitude, nextData.longitude)) {
      setMapCenter([Number(nextData.latitude), Number(nextData.longitude)]);
    }
  };

  const handleMapClick = (lat, lng) => {
    if (!hasValidCoordinates(lat, lng)) return;
    setFormData(prev => ({
      ...prev,
      latitude: lat.toFixed(6),
      longitude: lng.toFixed(6)
    }));
    setMapCenter([lat, lng]);
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!formData.reference || !formData.adresse || !hasValidCoordinates(formData.latitude, formData.longitude)) {
      toast.error('Renseignez une référence, une adresse et des coordonnées valides.');
      return;
    }
    onSubmit({
      ...formData,
      latitude: parseFloat(formData.latitude),
      longitude: parseFloat(formData.longitude),
    });
  };

  const handleDialogKeyDown = (event) => {
    if (event.key === 'Escape' && !isLoading) onClose();
    if (event.key !== 'Tab') return;
    const controls = [...event.currentTarget.querySelectorAll('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), a[href], [tabindex]:not([tabindex="-1"])')];
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

  const hasCoords = hasValidCoordinates(formData.latitude, formData.longitude);
  const markerPosition = hasCoords ? [parseFloat(formData.latitude), parseFloat(formData.longitude)] : null;

  return (
    <div className="modal-overlay">
      <div className="modal-content entity-form-modal large-modal point-form-modal" role="dialog" aria-modal="true" aria-labelledby="point-form-title" aria-describedby="point-form-description" onKeyDown={handleDialogKeyDown}>
        <div className="modal-header">
          <div>
            <h2 id="point-form-title">{point ? 'Modifier le point de collecte' : 'Ajouter un point de collecte'}</h2>
            <p id="point-form-description" className="modal-subtitle">Identifiez le point et placez-le sur la carte. Les champs marqués * sont obligatoires.</p>
          </div>
          <button type="button" className="close-btn" onClick={onClose} disabled={isLoading} aria-label="Fermer le formulaire"><FiX aria-hidden="true" /></button>
        </div>
        <form onSubmit={handleSubmit}>
          <div className="modal-body modal-split-layout">
            
            <div className="form-column">
              <section className="form-section" aria-labelledby="point-identity-title">
                <h3 id="point-identity-title" className="form-section-title">Informations du point</h3>
                <div className="form-grid">
              <div className="form-group">
                <label htmlFor="point-reference">Référence *</label>
                <input ref={firstInputRef} id="point-reference" type="text" name="reference" value={formData.reference} onChange={handleChange} required placeholder="Ex. PC-001" />
              </div>
              <div className="form-group">
                <label htmlFor="point-zone">Zone ou quartier</label>
                <input id="point-zone" type="text" name="zone" value={formData.zone} onChange={handleChange} placeholder="Ex. Centre-ville" />
              </div>
              <div className="form-group full-width">
                <label htmlFor="point-adresse">Adresse complète *</label>
                <input id="point-adresse" type="text" name="adresse" value={formData.adresse} onChange={handleChange} required placeholder="Rue, numéro, ville" />
              </div>
                </div>
              </section>
              <section className="form-section" aria-labelledby="point-coordinates-title">
                <h3 id="point-coordinates-title" className="form-section-title">Coordonnées géographiques</h3>
                <p id="point-coordinates-help" className="field-help">Cliquez sur la carte ou saisissez les coordonnées en degrés décimaux.</p>
                <div className="form-grid">
              <div className="form-group">
                <label htmlFor="point-latitude">Latitude *</label>
                <input id="point-latitude" type="number" step="any" min="-90" max="90" name="latitude" value={formData.latitude} onChange={handleChange} required placeholder="36.722857" aria-describedby="point-coordinates-help" />
              </div>
              <div className="form-group">
                <label htmlFor="point-longitude">Longitude *</label>
                <input id="point-longitude" type="number" step="any" min="-180" max="180" name="longitude" value={formData.longitude} onChange={handleChange} required placeholder="10.283535" aria-describedby="point-coordinates-help" />
              </div>
                </div>
              </section>
              <fieldset className="form-section">
                <legend className="form-section-title">Type de déchet</legend>
                <div className="type-grid">
                  {TYPE_DECHET_OPTIONS.map(opt => (
                    <label key={opt.value} className={`type-option ${formData.typeDechet === opt.value ? 'selected' : ''}`}>
                      <input type="radio" name="typeDechet" value={opt.value} checked={formData.typeDechet === opt.value} onChange={handleChange} />
                      {opt.label}
                    </label>
                  ))}
                </div>
              </fieldset>
              
              <div className="checkbox-field">
                <input type="checkbox" id="point-actif" name="actif" checked={formData.actif} onChange={handleChange} aria-describedby="point-actif-help" />
                <div>
                  <label htmlFor="point-actif">Point actif</label>
                  <p id="point-actif-help" className="field-help">Disponible lors de la planification des tournées.</p>
                </div>
              </div>
            </div>

            <section className="map-column" aria-labelledby="point-map-title">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                <h3 id="point-map-title" className="form-section-title" style={{ margin: 0 }}>Position sur la carte</h3>
                <button
                  type="button"
                  onClick={handleUseMyLocation}
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 6,
                    padding: '4px 10px',
                    fontSize: 12,
                    fontWeight: 600,
                    background: '#f1f5f9',
                    color: 'var(--primary, #166534)',
                    border: '1px solid #cbd5e1',
                    borderRadius: 6,
                    cursor: 'pointer'
                  }}
                  title="Utiliser ma position GPS actuelle"
                >
                  <FiNavigation size={12} />
                  <span>Ma position</span>
                </button>
              </div>
              <p className="field-help">Un clic place le repère et met à jour les coordonnées.</p>
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
                  <MapResizeObserver />
                  <ModalMapClickHandler onMapClick={handleMapClick} />
                  {markerPosition && (
                    <Marker position={markerPosition} />
                  )}
                </MapContainer>
              </div>
            </section>

          </div>
          <div className="modal-footer">
            <button type="button" className="btn-secondary" onClick={onClose} disabled={isLoading}>Annuler</button>
            <button type="submit" className="btn-primary" disabled={isLoading}>
              {isLoading ? 'Enregistrement…' : point ? 'Enregistrer les modifications' : 'Créer le point'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default PointCollecteFormModal;
